import type { DeviceInfo } from "@simwire/protocol";
import { DEFAULT_DEVICE_PORT } from "@simwire/protocol";
import { loadConfig } from "./config.js";
import { discoverDevice } from "./discovery.js";
import type { ReconnectOptions } from "./simwire.js";
import { Simwire } from "./simwire.js";
import type { Transport } from "./transport.js";
import { openWsTransport } from "./ws.js";

export interface ConnectOptions {
  /** Skip discovery and connect to this address. */
  host?: string;
  port?: number;
  /** Pairing token. Defaults to the one stored by `simwire pair`. */
  token?: string;
  clientName?: string;
  timeoutMs?: number;
  /** Retry after a dropped connection. On by default; pass false to opt out. */
  reconnect?: boolean | ReconnectOptions;
}

export async function connect(options: ConnectOptions = {}): Promise<Simwire> {
  const timeoutMs = options.timeoutMs ?? 10_000;
  const stored = loadConfig();
  const token = options.token ?? stored?.token;
  if (!token) {
    throw new Error(
      "No pairing token found. Run `npx simwire pair` first, or pass connect({ token }).",
    );
  }

  const open = async (): Promise<{ transport: Transport; device: DeviceInfo }> => {
    // Resolved on every attempt: a phone that rejoins the network can come
    // back on a different address.
    const latest = loadConfig();
    let host = options.host ?? latest?.endpoint.host;
    let port = options.port ?? (options.host ? DEFAULT_DEVICE_PORT : latest?.endpoint.port);
    if (!host) {
      const found = await discoverDevice(timeoutMs);
      host = found.host;
      port = found.port;
    }
    return openWsTransport({
      host,
      port: port ?? DEFAULT_DEVICE_PORT,
      token,
      clientName: options.clientName ?? "simwire-sdk",
      timeoutMs,
    });
  };

  const { transport, device } = await open();
  const reconnect: ReconnectOptions =
    options.reconnect === false
      ? { enabled: false }
      : options.reconnect === true || options.reconnect === undefined
        ? {}
        : options.reconnect;

  return new Simwire(transport, device, open, reconnect);
}
