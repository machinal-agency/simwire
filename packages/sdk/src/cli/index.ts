#!/usr/bin/env node
import { Command } from "commander";
import { doctorCommand } from "./doctor.js";
import { listenCommand } from "./listen.js";
import { pairCommand } from "./pair.js";
import { sendCommand } from "./send.js";
import { unpairCommand } from "./unpair.js";

const program = new Command();

program
  .name("simwire")
  .description("Send and receive real SMS through your own Android phone.")
  .version("0.2.0");

program
  .command("pair")
  .description("Pair this machine with the simwire Android app (QR code)")
  .action(pairCommand);

program
  .command("send")
  .description("Send an SMS")
  .argument("<to>", "recipient phone number (E.164, e.g. +250788123456)")
  .argument("<text>", "message body")
  .option("--sim <n>", "SIM slot to use (1 or 2)")
  .option("--no-wait", "exit right after the device queues the message")
  .option("--host <host>", "device address (skips discovery)")
  .option("--port <port>", "device port")
  .option("--mock", "use the built-in mock device (no phone needed)")
  .action(sendCommand);

program
  .command("listen")
  .description("Print incoming SMS live; optionally forward them to a local URL")
  .option("--forward <url>", "POST each incoming SMS to this URL (e.g. http://localhost:3000/sms)")
  .option("--host <host>", "device address (skips discovery)")
  .option("--port <port>", "device port")
  .option("--mock", "use the built-in mock device (no phone needed)")
  .action(listenCommand);

program
  .command("doctor")
  .description("Diagnose pairing, discovery and device connectivity")
  .action(doctorCommand);

program
  .command("unpair")
  .description("Forget the paired phone on this machine")
  .action(unpairCommand);

program.parseAsync().catch((err: Error) => {
  console.error(`\n✗ ${err.message}`);
  process.exit(1);
});
