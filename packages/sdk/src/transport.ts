import type { ClientFrame, DeviceFrame } from "@simwire/protocol";

export interface TransportEvents extends Record<string, unknown> {
  frame: DeviceFrame;
  close: { reason: string };
}

export interface Transport {
  send(frame: ClientFrame): void;
  onFrame(fn: (frame: DeviceFrame) => void): () => void;
  onClose(fn: (info: { reason: string }) => void): () => void;
  close(): void;
}
