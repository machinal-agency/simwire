import type { DeviceFrame, DeviceInfo, SendFrame } from "@simwire/protocol";
import { randomUUID } from "node:crypto";
import type { ConnectOptions } from "./connect.js";
import { Emitter } from "./events.js";
import { SentMessage } from "./message.js";
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

interface SimwireEvents extends Record<string, unknown> {
  message: IncomingMessage;
  status: { message: SentMessage };
  state: DeviceState;
  disconnect: { reason: string };
}

export class Simwire extends Emitter<SimwireEvents> {
  // Wired in index.ts (implementations live in connect.ts / mock.ts).
  static connect: (options?: ConnectOptions) => Promise<Simwire>;
  static mock: (options?: MockOptions) => MockSimwire;

  #pending = new Map<string, SentMessage>();
  #device: DeviceInfo;
  #transport: Transport;
  #closed = false;

  /** @internal — use `Simwire.connect()` or `Simwire.mock()`. */
  constructor(transport: Transport, device: DeviceInfo) {
    super();
    this.#transport = transport;
    this.#device = device;
    transport.onFrame((frame) => this.#handleFrame(frame));
    transport.onClose(({ reason }) => {
      if (!this.#closed) this.emit("disconnect", { reason });
    });
  }

  get device(): DeviceInfo {
    return this.#device;
  }

  async send(options: SendOptions): Promise<SentMessage> {
    const to = Array.isArray(options.to) ? options.to : [options.to];
    if (to.length === 0) throw new Error("send() requires at least one recipient");
    if (!options.text) throw new Error("send() requires a non-empty text");

    const id = randomUUID();
    const message = new SentMessage(id, to, options.text);
    this.#pending.set(id, message);

    const frame: SendFrame = { type: "send", id, to, text: options.text };
    if (options.simSlot !== undefined) frame.simSlot = options.simSlot;
    this.#transport.send(frame);

    // "queued" is emitted by the device only after persisting the message,
    // so resolving here means the SMS survives an app kill.
    await message.waitForStatus("queued", options.queueTimeoutMs ?? 10_000);
    return message;
  }

  close(): void {
    this.#closed = true;
    this.#transport.close();
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
