import { networkInterfaces } from "node:os";
import pc from "picocolors";
import type { MockSimwire } from "../mock.js";
import { mock } from "../mock.js";
import type { Simwire } from "../simwire.js";
import { connect } from "../connect.js";

export function lanIpv4(): string | null {
  for (const nets of Object.values(networkInterfaces())) {
    for (const net of nets ?? []) {
      if (net.family === "IPv4" && !net.internal) return net.address;
    }
  }
  return null;
}

export interface DeviceFlags {
  mock?: boolean;
  host?: string;
  port?: string;
}

/** `--mock` gives every command a phone-free demo mode. */
export async function openFromFlags(flags: DeviceFlags): Promise<Simwire | MockSimwire> {
  if (flags.mock) return mock({ latencyMs: 300 });
  return connect({
    host: flags.host,
    port: flags.port ? Number(flags.port) : undefined,
    clientName: "simwire-cli",
  });
}

export const ui = {
  ok: (text: string) => console.log(`${pc.green("✓")} ${text}`),
  fail: (text: string) => console.log(`${pc.red("✗")} ${text}`),
  info: (text: string) => console.log(`${pc.dim("·")} ${text}`),
  title: (text: string) => console.log(`\n${pc.bold(text)}\n`),
  hint: (text: string) => console.log(`  ${pc.dim(text)}`),
};
