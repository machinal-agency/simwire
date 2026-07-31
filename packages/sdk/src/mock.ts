import type {
  ClientFrame,
  DeviceFrame,
  DeviceInfo,
  MessageStatus,
  SendFrame,
} from "@simwire/protocol";
import { TimeoutError } from "./message.js";
import type { IncomingMessage, ReconnectOptions, Reopen } from "./simwire.js";
import { Simwire } from "./simwire.js";
import type { Transport } from "./transport.js";

export interface MockOptions {
  /** Delay between each simulated status hop (queued -> sent -> delivered). */
  latencyMs?: number;
  /** Emit "delivered" after "sent". Defaults to true. */
  autoDeliver?: boolean;
  device?: Partial<Omit<DeviceInfo, "simSlots">>;
  /** Tune how `simulateDrop()` recovers, or switch recovery off entirely. */
  reconnect?: ReconnectOptions;
}

const MOCK_DEVICE: DeviceInfo = {
  id: "mock-device",
  name: "Mock Device",
  model: "Simwire Virtual",
  androidVersion: "15",
  simSlots: [
    { index: 0, carrier: "Mock Carrier", phoneNumber: "+15550100000" },
    { index: 1, carrier: null, phoneNumber: null },
  ],
};

class MockTransport implements Transport {
  readonly device: DeviceInfo;
  #frameHandlers = new Set<(frame: DeviceFrame) => void>();
  #closeHandlers = new Set<(info: { reason: string }) => void>();
  #timers = new Set<ReturnType<typeof setTimeout>>();
  #closed = false;
  #failNextReason: string | null = null;

  constructor(private readonly options: Required<Pick<MockOptions, "latencyMs" | "autoDeliver">> & MockOptions) {
    this.device = { ...MOCK_DEVICE, ...options.device };
  }

  send(frame: ClientFrame): void {
    if (this.#closed) throw new Error("Transport is closed");
    if (frame.type === "send") this.#simulateDelivery(frame);
    if (frame.type === "ping") this.#emit({ type: "pong" });
  }

  failNext(reason: string): void {
    this.#failNextReason = reason;
  }

  deliverIncoming(frame: DeviceFrame): void {
    this.#emit(frame);
  }

  onFrame(fn: (frame: DeviceFrame) => void): () => void {
    this.#frameHandlers.add(fn);
    return () => this.#frameHandlers.delete(fn);
  }

  onClose(fn: (info: { reason: string }) => void): () => void {
    this.#closeHandlers.add(fn);
    return () => this.#closeHandlers.delete(fn);
  }

  close(): void {
    this.#shutdown("closed by client");
  }

  /** Simulate the network going away rather than a clean shutdown. */
  drop(reason: string): void {
    this.#shutdown(reason);
  }

  #shutdown(reason: string): void {
    this.#closed = true;
    for (const timer of this.#timers) clearTimeout(timer);
    this.#timers.clear();
    for (const fn of [...this.#closeHandlers]) fn({ reason });
  }

  #simulateDelivery(frame: SendFrame): void {
    const failReason = this.#failNextReason;
    this.#failNextReason = null;

    const steps: Array<{ status: MessageStatus; error?: string }> = [{ status: "queued" }];
    if (failReason) {
      steps.push({ status: "failed", error: failReason });
    } else {
      steps.push({ status: "sent" });
      if (this.options.autoDeliver) steps.push({ status: "delivered" });
    }

    steps.forEach((step, i) => {
      this.#after(this.options.latencyMs * (i + 1), () => {
        this.#emit({
          type: "message.status",
          id: frame.id,
          status: step.status,
          ...(step.error !== undefined ? { error: step.error } : {}),
          at: new Date().toISOString(),
        });
      });
    });
  }

  #after(ms: number, fn: () => void): void {
    const timer = setTimeout(() => {
      this.#timers.delete(timer);
      if (!this.#closed) fn();
    }, ms);
    this.#timers.add(timer);
  }

  #emit(frame: DeviceFrame): void {
    for (const fn of [...this.#frameHandlers]) fn(frame);
  }
}

export interface SimulateIncomingOptions {
  from: string;
  text: string;
  simSlot?: number;
  receivedAt?: Date;
}

/**
 * Drop-in replacement for a paired phone: same API surface, no hardware.
 * Designed for unit tests and CI.
 */
export class MockSimwire extends Simwire {
  readonly outbox: Array<Awaited<ReturnType<Simwire["send"]>>> = [];
  readonly inbox: IncomingMessage[] = [];
  #live: { current: MockTransport };

  /** @internal — use `mock()`. */
  constructor(live: { current: MockTransport }, reopen?: Reopen, reconnect?: ReconnectOptions) {
    super(live.current, live.current.device, reopen, reconnect);
    this.#live = live;
    this.on("message", (m) => this.inbox.push(m));
  }

  get #transport(): MockTransport {
    return this.#live.current;
  }

  override async send(options: Parameters<Simwire["send"]>[0]): ReturnType<Simwire["send"]> {
    const message = await super.send(options);
    this.outbox.push(message);
    return message;
  }

  /** Make the next `send()` fail after being queued. */
  failNext(reason = "generic failure"): void {
    this.#transport.failNext(reason);
  }

  /**
   * Drop the connection as if the network had gone away, so tests can cover
   * what the app does while the phone is unreachable. Unless reconnection was
   * disabled, the session comes back on its own.
   */
  simulateDrop(reason = "network lost"): void {
    this.#transport.drop(reason);
  }

  /** Inject an incoming SMS as if the SIM had received it. */
  simulateIncoming(options: SimulateIncomingOptions): void {
    this.#transport.deliverIncoming({
      type: "message.incoming",
      from: options.from,
      text: options.text,
      simSlot: options.simSlot ?? 0,
      receivedAt: (options.receivedAt ?? new Date()).toISOString(),
    });
  }

  /** Resolve on the next incoming message matching the predicate. */
  waitForMessage(
    predicate: (m: IncomingMessage) => boolean = () => true,
    timeoutMs = 5_000,
  ): Promise<IncomingMessage> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        off();
        reject(new TimeoutError("an incoming message", timeoutMs));
      }, timeoutMs);
      const off = this.on("message", (m) => {
        if (!predicate(m)) return;
        clearTimeout(timer);
        off();
        resolve(m);
      });
    });
  }
}

export function mock(options: MockOptions = {}): MockSimwire {
  const settings = {
    ...options,
    latencyMs: options.latencyMs ?? 5,
    autoDeliver: options.autoDeliver ?? true,
  };
  const live = { current: new MockTransport(settings) };
  const reopen = async () => {
    live.current = new MockTransport(settings);
    return { transport: live.current, device: live.current.device };
  };
  return new MockSimwire(live, reopen, options.reconnect);
}
