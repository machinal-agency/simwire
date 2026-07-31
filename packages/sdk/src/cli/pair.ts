import type { PairingQrPayload, PairingRequest, PairingResponse } from "@simwire/protocol";
import { PROTOCOL_VERSION } from "@simwire/protocol";
import { randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { hostname } from "node:os";
import pc from "picocolors";
import qrcode from "qrcode-terminal";
import { saveConfig } from "../config.js";
import { lanIpv4, ui } from "./util.js";

const PAIRING_TIMEOUT_MS = 5 * 60_000;

export async function pairCommand(): Promise<void> {
  const host = lanIpv4();
  if (!host) {
    ui.fail("No LAN IPv4 address found. Connect this machine to Wi-Fi or Ethernet first.");
    process.exitCode = 1;
    return;
  }

  const code = randomBytes(4).toString("hex");
  const clientName = hostname();

  await new Promise<void>((resolve) => {
    const server = createServer((req, res) => {
      if (req.method !== "POST" || req.url !== "/pair") {
        res.writeHead(404).end();
        return;
      }
      let body = "";
      req.on("data", (chunk) => (body += chunk));
      req.on("end", () => {
        let request: PairingRequest;
        try {
          request = JSON.parse(body) as PairingRequest;
        } catch {
          res.writeHead(400).end();
          return;
        }
        if (request.code !== code) {
          res.writeHead(403).end(JSON.stringify({ ok: false }));
          return;
        }
        saveConfig({
          endpoint: request.endpoint,
          token: request.token,
          deviceName: request.device.name,
        });
        const response: PairingResponse = { ok: true, clientName };
        res.writeHead(200, { "content-type": "application/json" }).end(JSON.stringify(response));

        ui.ok(`Paired with ${pc.bold(request.device.name)} (${request.device.model})`);
        const sims = request.device.simSlots.filter((s) => s.carrier);
        for (const sim of sims) {
          ui.info(`SIM ${sim.index + 1}: ${sim.carrier}${sim.phoneNumber ? ` — ${sim.phoneNumber}` : ""}`);
        }
        console.log(`\nTry it:\n  ${pc.cyan("npx simwire send +1234567890 \"hello from simwire\"")}\n`);
        clearTimeout(timer);
        server.close();
        resolve();
      });
    });

    const timer = setTimeout(() => {
      ui.fail("Pairing timed out after 5 minutes.");
      server.close();
      process.exitCode = 1;
      resolve();
    }, PAIRING_TIMEOUT_MS);
    timer.unref?.();

    server.listen(0, () => {
      const address = server.address();
      const port = typeof address === "object" && address ? address.port : 0;
      const payload: PairingQrPayload = { v: PROTOCOL_VERSION, host, port, code };

      ui.title("Pair your phone");
      qrcode.generate(JSON.stringify(payload), { small: true });
      console.log(`Open the ${pc.bold("simwire")} app on your Android phone and scan this code.`);
      ui.hint(`Both devices must be on the same network (${host}).`);
      ui.hint("Waiting for the phone…");
    });
  });
}
