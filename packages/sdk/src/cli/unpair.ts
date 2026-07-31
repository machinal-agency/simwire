import pc from "picocolors";
import { clearConfig, configPath, loadConfig } from "../config.js";
import { ui } from "./util.js";

export function unpairCommand(): void {
  const config = loadConfig();
  if (!config) {
    ui.info(`Nothing to forget: no pairing stored at ${configPath()}`);
    return;
  }

  clearConfig();
  ui.ok(`Forgot ${pc.bold(config.deviceName ?? "the paired device")} on this machine`);
  ui.hint("The phone still trusts this computer until you unpair it there too:");
  ui.hint("open the simwire app, go to Health, and tap Unpair.");
  console.log(`\nTo pair again:\n  ${pc.cyan("npx simwire pair")}\n`);
}
