import type { MessageStatus } from "@simwire/protocol";
import { Emitter } from "./events.js";

/** Local lifecycle: "pending" until the device durably queues the message. */
export type SentMessageStatus = "pending" | MessageStatus;

const STATUS_ORDER: Record<SentMessageStatus, number> = {
  pending: 0,
  queued: 1,
  sent: 2,
  delivered: 3,
  failed: 4,
};

export class MessageFailedError extends Error {
  constructor(
    public readonly messageId: string,
    public readonly reason: string,
  ) {
    super(`Message ${messageId} failed: ${reason}`);
    this.name = "MessageFailedError";
  }
}

export class TimeoutError extends Error {
  constructor(what: string, ms: number) {
    super(`Timed out after ${ms}ms waiting for ${what}`);
    this.name = "TimeoutError";
  }
}

interface MessageEvents extends Record<string, unknown> {
  status: { status: SentMessageStatus; error?: string };
}

/** Handle returned by `send()`, tracks the lifecycle of one outgoing SMS. */
export class SentMessage extends Emitter<MessageEvents> {
  #status: SentMessageStatus = "pending";
  #error?: string;

  constructor(
    public readonly id: string,
    public readonly to: string[],
    public readonly text: string,
  ) {
    super();
  }

  get status(): SentMessageStatus {
    return this.#status;
  }

  get error(): string | undefined {
    return this.#error;
  }

  /** @internal */
  _update(status: MessageStatus, error?: string): void {
    // Frames can be replayed out of order on reconnect; never regress.
    if (STATUS_ORDER[status] <= STATUS_ORDER[this.#status]) return;
    this.#status = status;
    this.#error = error;
    this.emit("status", { status, error });
  }

  async waitForStatus(
    target: "queued" | "sent" | "delivered",
    timeoutMs = 30_000,
  ): Promise<void> {
    if (this.#status === "failed") throw new MessageFailedError(this.id, this.#error ?? "unknown");
    if (STATUS_ORDER[this.#status] >= STATUS_ORDER[target]) return;

    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        off();
        reject(new TimeoutError(`status "${target}"`, timeoutMs));
      }, timeoutMs);
      const off = this.on("status", ({ status, error }) => {
        if (status === "failed") {
          cleanup();
          reject(new MessageFailedError(this.id, error ?? "unknown"));
        } else if (STATUS_ORDER[status] >= STATUS_ORDER[target]) {
          cleanup();
          resolve();
        }
      });
      const cleanup = () => {
        clearTimeout(timer);
        off();
      };
    });
  }

  waitForDelivery(timeoutMs = 60_000): Promise<void> {
    return this.waitForStatus("delivered", timeoutMs);
  }
}
