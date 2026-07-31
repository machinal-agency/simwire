import pc from "picocolors";
import type { DeviceFlags } from "./util.js";
import { openFromFlags, ui } from "./util.js";

interface SendFlags extends DeviceFlags {
  sim?: string;
  wait?: boolean;
}

export async function sendCommand(to: string, text: string, flags: SendFlags): Promise<void> {
  const sms = await openFromFlags(flags);
  try {
    const message = await sms.send({
      to,
      text,
      simSlot: flags.sim ? Number(flags.sim) - 1 : undefined,
    });
    ui.ok(`Queued on ${pc.bold(sms.device.name)} (id ${pc.dim(message.id.slice(0, 8))})`);

    message.on("status", ({ status, error }) => {
      if (status === "failed") ui.fail(`Failed: ${error}`);
      else ui.ok(status[0]?.toUpperCase() + status.slice(1));
    });

    if (flags.wait === false) return;
    await message.waitForDelivery().catch((err: Error) => {
      if (err.name === "TimeoutError") ui.info("No delivery report yet (carrier-dependent). Message was sent.");
      else throw err;
    });
  } finally {
    sms.close();
  }
}
