import pc from "picocolors";
import { configPath, loadConfig } from "../config.js";
import { connect } from "../connect.js";
import { discoverDevice } from "../discovery.js";
import { lanIpv4, ui } from "./util.js";

export async function doctorCommand(): Promise<void> {
  ui.title("simwire doctor");
  let healthy = true;

  const ip = lanIpv4();
  if (ip) ui.ok(`LAN address: ${ip}`);
  else {
    healthy = false;
    ui.fail("No LAN IPv4 address — connect to Wi-Fi or Ethernet.");
  }

  const config = loadConfig();
  if (config) {
    ui.ok(`Paired with ${pc.bold(config.deviceName ?? "device")} (${config.endpoint.host}:${config.endpoint.port})`);
  } else {
    healthy = false;
    ui.fail(`No pairing found at ${configPath()}`);
    ui.hint("Run: npx simwire pair");
  }

  try {
    const found = await discoverDevice(3_000);
    ui.ok(`mDNS discovery: ${found.name} at ${found.host}:${found.port}`);
  } catch {
    ui.fail("mDNS discovery found no device (app closed, other network, or client isolation).");
    ui.hint("This is not fatal if pairing stored an address, but auto-discovery won't work.");
    if (!config) healthy = false;
  }

  if (config) {
    try {
      const sms = await connect({ clientName: "simwire-doctor", timeoutMs: 5_000 });
      ui.ok(`Device reachable: ${sms.device.model}, Android ${sms.device.androidVersion}`);
      const sims = sms.device.simSlots.filter((s) => s.carrier);
      if (sims.length === 0) {
        healthy = false;
        ui.fail("No active SIM detected on the device.");
      } else {
        for (const sim of sims) ui.ok(`SIM ${sim.index + 1}: ${sim.carrier}`);
      }
      sms.close();
    } catch (err) {
      healthy = false;
      ui.fail(`Cannot reach device: ${err instanceof Error ? err.message : String(err)}`);
      ui.hint("Is the simwire app open? Same Wi-Fi? Battery optimization may have killed it —");
      ui.hint("the app's Health screen shows how to whitelist it for your phone brand.");
    }
  }

  console.log();
  if (healthy) ui.ok(pc.green("Everything looks good."));
  else {
    ui.fail(pc.red("Some checks failed — see hints above."));
    process.exitCode = 1;
  }
}
