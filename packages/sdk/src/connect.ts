import { DEFAULT_DEVICE_PORT } from "@simwire/protocol";
import { loadConfig } from "./config.js";
import { discoverDevice } from "./discovery.js";
import { Simwire } from "./simwire.js";
import { openWsTransport } from "./ws.js";

export interface ConnectOptions {
  /** Skip discovery and connect to this address. */
  host?: string;
  port?: number;
  /** Pairing token. Defaults to the one stored by `simwire pair`. */
  token?: string;
  clientName?: string;
  timeoutMs?: number;
}

export async function connect(options: ConnectOptions = {}): Promise<Simwire> {
  const stored = loadConfig();
  const timeoutMs = options.timeoutMs ?? 10_000;

  const token = options.token ?? stored?.token;
  if (!token) {
    throw new Error(
      "No pairing token found. Run `npx simwire pair` first, or pass connect({ token }).",
    );
  }

  let host = options.host ?? stored?.endpoint.host;
  let port = options.port ?? (options.host ? DEFAULT_DEVICE_PORT : stored?.endpoint.port);
  if (!host) {
    const found = await discoverDevice(timeoutMs);
    host = found.host;
    port = found.port;
  }

  const { transport, device } = await openWsTransport({
    host,
    port: port ?? DEFAULT_DEVICE_PORT,
    token,
    clientName: options.clientName ?? "simwire-sdk",
    timeoutMs,
  });
  return new Simwire(transport, device);
}
