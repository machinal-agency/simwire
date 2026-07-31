import type { DeviceFrame, DeviceInfo, SendFrame } from "@simwire/protocol";
import { randomUUID } from "node:crypto";
import type { ConnectOptions } from "./connect.js";
import { Emitter } from "./events.js";
import { SentMessage, TimeoutError } from "./message.js";
import type { MockOptions, MockSimwire } from "./mock.js";
import type { Transport } from "./transport.js";

export interface IncomingMessage {
  from: string;
  text: string;
  simSlot: number;
  receivedAt: Date;
}

export interface DeviceState {
  battery: number;
  charging: boolean;
  network: "wifi" | "cellular" | "offline";
}

export interface SendOptions {
  to: string | string[];
  text: string;
  simSlot?: number;
  /** How long to wait for the device to durably queue the message. */
  queueTimeoutMs?: number;
}

export interface ReconnectOptions {
  /** Defaults to true whenever the session was opened by `connect()`. */
  enabled?: boolean;
  initialDelayMs?: number;
  maxDelayMs?: number;
  /** Give up after this many consecutive failures. Defaults to unlimited. */
  maxAttempts?: number;
}

/** Reopens the session after a drop: a fresh socket and a fresh handshake. */
export type Reopen = () => Promise<{ transport: Transport; device: DeviceInfo }>;

interface SimwireEvents extends Record<string, unknown> {
  message: IncomingMessage;
  status: { message: SentMessage };
  state: DeviceState;
  disconnect: { reason: string; willRetry: boolean };
  reconnecting: { attempt: number; delayMs: number };
  reconnected: { device: DeviceInfo; downtimeMs: number };
}

const DEFAULTS = { initialDelayMs: 500, maxDelayMs: 15_000, maxAttempts: Infinity };

export class Simwire extends Emitter<SimwireEvents> {
  // Wired in index.ts (implementations live in connect.ts / mock.ts).
  static connect: (options?: ConnectOptions) => Promise<Simwire>;
  static mock: (options?: MockOptions) => MockSimwire;

  #pending = new Map<string, SentMessage>();
  #device: DeviceInfo;
  #transport: Transport;
  #detach: Array<() => void> = [];
  #closed = false;
  #connected = true;
  #reopen?: Reopen;
  #reconnect: Required<Omit<ReconnectOptions, "enabled">> & { enabled: boolean };
  #timer?: ReturnType<typeof setTimeout>;
  #waiters: Array<() => void> = [];

  /** @internal — use `Simwire.connect()` or `Simwire.mock()`. */
  constructor(
    transport: Transport,
    device: DeviceInfo,
    reopen?: Reopen,
    reconnect: ReconnectOptions = {},
  ) {
    super();
    this.#device = device;
    this.#reopen = reopen;
    this.#reconnect = {
      enabled: reconnect.enabled ?? Boolean(reopen),
      initialDelayMs: reconnect.initialDelayMs ?? DEFAULTS.initialDelayMs,
      maxDelayMs: reconnect.maxDelayMs ?? DEFAULTS.maxDelayMs,
      maxAttempts: reconnect.maxAttempts ?? DEFAULTS.maxAttempts,
    };
    this.#transport = transport;
    this.#attach(transport);
  }

  get device(): DeviceInfo {
    return this.#device;
  }

  /** False while the socket is down and a retry is pending. */
  get connected(): boolean {
    return this.#connected && !this.#closed;
  }

  async send(options: SendOptions): Promise<SentMessage> {
    const to = Array.isArray(options.to) ? options.to : [options.to];
    if (to.length === 0) throw new Error("send() requires at least one recipient");
    if (!options.text) throw new Error("send() requires a non-empty text");

    const timeoutMs = options.queueTimeoutMs ?? 10_000;
    // A drop mid-flight should not surface as a hard failure: wait for the
    // retry loop to bring the session back, within the caller's own budget.
    if (!this.connected) await this.#awaitConnection(timeoutMs);

    const id = randomUUID();
    const message = new SentMessage(id, to, options.text);
    this.#pending.set(id, message);

    const frame: SendFrame = { type: "send", id, to, text: options.text };
    if (options.simSlot !== undefined) frame.simSlot = options.simSlot;
    this.#transport.send(frame);

    // "queued" is emitted by the device only after persisting the message,
    // so resolving here means the SMS survives an app kill.
    await message.waitForStatus("queued", timeoutMs);
    return message;
  }

  close(): void {
    this.#closed = true;
    this.#connected = false;
    if (this.#timer) clearTimeout(this.#timer);
    this.#releaseWaiters();
    this.#transport.close();
  }

  #attach(transport: Transport): void {
    for (const off of this.#detach) off();
    this.#detach = [
      transport.onFrame((frame) => this.#handleFrame(frame)),
      transport.onClose(({ reason }) => this.#onClose(reason)),
    ];
  }

  #onClose(reason: string): void {
    if (this.#closed) return;
    this.#connected = false;
    const willRetry = this.#reconnect.enabled && Boolean(this.#reopen);
    this.emit("disconnect", { reason, willRetry });
    if (willRetry) void this.#retry(1, Date.now());
  }

  async #retry(attempt: number, downSince: number): Promise<void> {
    if (this.#closed) return;
    if (attempt > this.#reconnect.maxAttempts) {
      this.#releaseWaiters();
      return;
    }

    const delayMs = Math.min(
      this.#reconnect.initialDelayMs * 2 ** (attempt - 1),
      this.#reconnect.maxDelayMs,
    );
    this.emit("reconnecting", { attempt, delayMs });
    await new Promise<void>((resolve) => {
      this.#timer = setTimeout(resolve, delayMs);
    });
    if (this.#closed) return;

    try {
      const { transport, device } = await this.#reopen!();
      if (this.#closed) {
        transport.close();
        return;
      }
      this.#transport = transport;
      this.#device = device;
      this.#connected = true;
      this.#attach(transport);
      this.emit("reconnected", { device, downtimeMs: Date.now() - downSince });
      this.#releaseWaiters();
    } catch {
      void this.#retry(attempt + 1, downSince);
    }
  }

  #awaitConnection(timeoutMs: number): Promise<void> {
    if (this.#closed) return Promise.reject(new Error("Session is closed"));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.#waiters = this.#waiters.filter((w) => w !== wake);
        reject(new TimeoutError("the device to come back online", timeoutMs));
      }, timeoutMs);
      const wake = () => {
        clearTimeout(timer);
        this.connected ? resolve() : reject(new Error("Reconnection gave up"));
      };
      this.#waiters.push(wake);
    });
  }

  #releaseWaiters(): void {
    const waiters = this.#waiters;
    this.#waiters = [];
    for (const wake of waiters) wake();
  }

  #handleFrame(frame: DeviceFrame): void {
    switch (frame.type) {
      case "message.status": {
        const message = this.#pending.get(frame.id);
        if (!message) return;
        const before = message.status;
        message._update(frame.status, frame.error);
        if (message.status !== before) {
          this.emit("status", { message });
        }
        if (frame.status === "delivered" || frame.status === "failed") {
          this.#pending.delete(frame.id);
        }
        break;
      }
      case "message.incoming":
        this.emit("message", {
          from: frame.from,
          text: frame.text,
          simSlot: frame.simSlot,
          receivedAt: new Date(frame.receivedAt),
        });
        break;
      case "device.state":
        this.emit("state", {
          battery: frame.battery,
          charging: frame.charging,
          network: frame.network,
        });
        break;
      default:
        break;
    }
  }
}
