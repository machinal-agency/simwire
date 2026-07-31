import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

export interface StoredConfig {
  endpoint: { host: string; port: number };
  token: string;
  deviceName?: string;
}

export function configPath(): string {
  return join(homedir(), ".simwire", "config.json");
}

export function loadConfig(): StoredConfig | null {
  try {
    const raw = readFileSync(configPath(), "utf8");
    const value = JSON.parse(raw) as StoredConfig;
    if (!value?.endpoint?.host || !value?.token) return null;
    return value;
  } catch {
    return null;
  }
}

export function saveConfig(config: StoredConfig): void {
  const path = configPath();
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${JSON.stringify(config, null, 2)}\n`, { mode: 0o600 });
}

/**
 * Forget the paired device on this machine. The phone keeps its own token
 * until it is unpaired there too, from the app's Health screen.
 */
export function clearConfig(): boolean {
  try {
    rmSync(configPath());
    return true;
  } catch {
    return false;
  }
}
