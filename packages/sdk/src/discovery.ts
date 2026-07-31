import { MDNS_SERVICE_TYPE } from "@simwire/protocol";
import { Bonjour } from "bonjour-service";
import { TimeoutError } from "./message.js";

export interface DiscoveredDevice {
  host: string;
  port: number;
  name: string;
}

/** Find the first simwire device advertising on the LAN. */
export function discoverDevice(timeoutMs = 5_000): Promise<DiscoveredDevice> {
  const bonjour = new Bonjour();
  // "_simwire._tcp" -> bonjour-service wants the bare type without markers.
  const type = MDNS_SERVICE_TYPE.replace(/^_/, "").replace(/\._tcp$/, "");

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      stop();
      reject(
        new TimeoutError(
          "a simwire device on the LAN (is the app running? try connect({ host }))",
          timeoutMs,
        ),
      );
    }, timeoutMs);

    const browser = bonjour.find({ type }, (service) => {
      const host = service.addresses?.find((a) => a.includes(".")) ?? service.addresses?.[0];
      if (!host) return;
      clearTimeout(timer);
      stop();
      resolve({ host, port: service.port, name: service.name });
    });

    const stop = () => {
      browser.stop();
      bonjour.destroy();
    };
  });
}
