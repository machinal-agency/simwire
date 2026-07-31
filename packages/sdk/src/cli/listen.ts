import pc from "picocolors";
import { MockSimwire } from "../mock.js";
import type { IncomingMessage } from "../simwire.js";
import type { DeviceFlags } from "./util.js";
import { openFromFlags, ui } from "./util.js";

interface ListenFlags extends DeviceFlags {
  forward?: string;
}

const MOCK_DEMO_MESSAGES = [
  { from: "+15550100001", text: "Your verification code is 428113" },
  { from: "+15550100002", text: "PAYMENT: you have received 2,500 RWF from +250788***21" },
];

export async function listenCommand(flags: ListenFlags): Promise<void> {
  const sms = await openFromFlags(flags);

  ui.ok(`Listening on ${pc.bold(sms.device.name)}${flags.mock ? pc.yellow(" (mock)") : ""}`);
  if (flags.forward) ui.info(`Forwarding incoming SMS to ${flags.forward}`);
  ui.hint("Press Ctrl+C to stop.\n");

  sms.on("message", (message) => {
    const time = message.receivedAt.toLocaleTimeString();
    console.log(`${pc.dim(time)} ${pc.cyan(message.from)} ${message.text}`);
    if (flags.forward) void forward(flags.forward, message);
  });

  sms.on("disconnect", ({ reason }) => {
    ui.fail(`Disconnected: ${reason}`);
    process.exit(1);
  });

  if (sms instanceof MockSimwire) {
    MOCK_DEMO_MESSAGES.forEach((demo, i) => {
      setTimeout(() => sms.simulateIncoming(demo), 1_200 * (i + 1));
    });
  }

  // The ref'd interval keeps the event loop alive until Ctrl+C
  // (a WebSocket holds it on its own, but the mock has no socket).
  const keepAlive = setInterval(() => {}, 60_000);
  await new Promise<void>((resolve) => {
    process.on("SIGINT", () => {
      clearInterval(keepAlive);
      sms.close();
      console.log();
      resolve();
    });
  });
}

async function forward(url: string, message: IncomingMessage): Promise<void> {
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        event: "sms.received",
        from: message.from,
        text: message.text,
        simSlot: message.simSlot,
        receivedAt: message.receivedAt.toISOString(),
      }),
    });
    if (!res.ok) ui.fail(`Forward failed: HTTP ${res.status}`);
  } catch (err) {
    ui.fail(`Forward failed: ${err instanceof Error ? err.message : String(err)}`);
  }
}
