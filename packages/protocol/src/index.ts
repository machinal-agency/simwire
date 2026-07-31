export const PROTOCOL_VERSION = 1;

/** mDNS service type advertised by the Android app on the LAN. */
export const MDNS_SERVICE_TYPE = "_simwire._tcp";

export const DEFAULT_DEVICE_PORT = 4650;

// ---------------------------------------------------------------------------
// Pairing
// ---------------------------------------------------------------------------

/**
 * Payload encoded in the QR code displayed by the CLI (`simwire pair`).
 * The phone scans it and calls back the pairing endpoint to complete the
 * exchange, so the user never types an IP on either side.
 */
export interface PairingQrPayload {
  v: typeof PROTOCOL_VERSION;
  /** LAN address of the machine running the CLI. */
  host: string;
  port: number;
  /** One-time code proving the phone scanned this specific QR. */
  code: string;
}

/** Sent by the phone to the CLI pairing endpoint after scanning the QR. */
export interface PairingRequest {
  v: typeof PROTOCOL_VERSION;
  code: string;
  device: DeviceInfo;
  /** Address the SDK should connect to from now on. */
  endpoint: { host: string; port: number };
  /** Bearer token the device will require on future connections. */
  token: string;
}

export interface PairingResponse {
  ok: boolean;
  clientName: string;
}

export interface DeviceInfo {
  id: string;
  name: string;
  model: string;
  androidVersion: string;
  simSlots: SimSlot[];
}

export interface SimSlot {
  index: number;
  carrier: string | null;
  phoneNumber: string | null;
}

// ---------------------------------------------------------------------------
// WebSocket frames — client (SDK/CLI) -> device
// ---------------------------------------------------------------------------

export interface HelloFrame {
  type: "hello";
  v: typeof PROTOCOL_VERSION;
  token: string;
  clientName: string;
}

export interface SendFrame {
  type: "send";
  /** Client-generated id, echoed back in status frames. */
  id: string;
  to: string[];
  text: string;
  simSlot?: number;
}

export interface PingFrame {
  type: "ping";
}

export type ClientFrame = HelloFrame | SendFrame | PingFrame;

// ---------------------------------------------------------------------------
// WebSocket frames — device -> client (SDK/CLI)
// ---------------------------------------------------------------------------

export interface HelloAckFrame {
  type: "hello.ack";
  device: DeviceInfo;
}

export type MessageStatus = "queued" | "sent" | "delivered" | "failed";

export interface StatusFrame {
  type: "message.status";
  id: string;
  status: MessageStatus;
  /** Present when status is "failed". */
  error?: string;
  at: string;
}

export interface IncomingFrame {
  type: "message.incoming";
  from: string;
  text: string;
  simSlot: number;
  receivedAt: string;
}

export interface DeviceStateFrame {
  type: "device.state";
  battery: number;
  charging: boolean;
  network: "wifi" | "cellular" | "offline";
}

export interface PongFrame {
  type: "pong";
}

export interface ErrorFrame {
  type: "error";
  code: "unauthorized" | "bad_frame" | "sms_unavailable" | "internal";
  message: string;
}

export type DeviceFrame =
  | HelloAckFrame
  | StatusFrame
  | IncomingFrame
  | DeviceStateFrame
  | PongFrame
  | ErrorFrame;

export type Frame = ClientFrame | DeviceFrame;
