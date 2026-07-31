import type { ClientFrame, DeviceFrame, DeviceInfo } from "@simwire/protocol";
import { PROTOCOL_VERSION } from "@simwire/protocol";
import WebSocket from "ws";
import { TimeoutError } from "./message.js";
import type { Transport } from "./transport.js";

const PING_INTERVAL_MS = 15_000;

export class WsTransport implements Transport {
  #frameHandlers = new Set<(frame: DeviceFrame) => void>();
  #closeHandlers = new Set<(info: { reason: string }) => void>();
  #pingTimer: ReturnType<typeof setInterval>;

  constructor(private readonly ws: WebSocket) {
    ws.on("message", (data) => {
      const frame = parseFrame(data.toString());
      if (!frame) return;
      for (const fn of [...this.#frameHandlers]) fn(frame);
    });
    ws.on("close", (_code, reason) => {
      clearInterval(this.#pingTimer);
      const text = reason.toString() || "connection closed";
      for (const fn of [...this.#closeHandlers]) fn({ reason: text });
    });
    this.#pingTimer = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) this.send({ type: "ping" });
    }, PING_INTERVAL_MS);
    this.#pingTimer.unref?.();
  }

  send(frame: ClientFrame): void {
    this.ws.send(JSON.stringify(frame));
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
    clearInterval(this.#pingTimer);
    this.ws.close(1000, "closed by client");
  }
}

function parseFrame(raw: string): DeviceFrame | null {
  try {
    const value = JSON.parse(raw) as DeviceFrame;
    return typeof value === "object" && value !== null && "type" in value ? value : null;
  } catch {
    return null;
  }
}

export interface OpenOptions {
  host: string;
  port: number;
  token: string;
  clientName: string;
  timeoutMs: number;
}

export function openWsTransport(
  options: OpenOptions,
): Promise<{ transport: WsTransport; device: DeviceInfo }> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://${options.host}:${options.port}/ws`, {
      handshakeTimeout: options.timeoutMs,
    });

    const timer = setTimeout(() => {
      ws.terminate();
      reject(new TimeoutError(`device handshake at ${options.host}:${options.port}`, options.timeoutMs));
    }, options.timeoutMs);

    ws.once("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });

    ws.once("open", () => {
      const transport = new WsTransport(ws);
      const offFrame = transport.onFrame((frame) => {
        if (frame.type === "hello.ack") {
          clearTimeout(timer);
          offFrame();
          resolve({ transport, device: frame.device });
        } else if (frame.type === "error") {
          clearTimeout(timer);
          offFrame();
          ws.close();
          reject(new Error(`Device rejected connection: ${frame.code} — ${frame.message}`));
        }
      });
      transport.send({
        type: "hello",
        v: PROTOCOL_VERSION,
        token: options.token,
        clientName: options.clientName,
      });
    });
  });
}
