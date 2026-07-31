# simwire

Send and receive real SMS from Node.js through your own Android phone.
No Twilio, no signup, no per-message fees: your SIM is the gateway.

```bash
npm install simwire
npx simwire pair        # scan the QR with the simwire Android app
```

```ts
import { connect } from "simwire";

const sms = await connect();                       // finds your phone on the LAN

const msg = await sms.send({
  to: "+15550142834",
  text: "Your code is 4821",
});
await msg.waitForDelivery();                       // real carrier status

sms.on("message", (m) => console.log(m.from, m.text));
```

Incoming messages stream straight to your machine over the LAN, so OTP flows
are testable end to end without a public webhook or a tunnel.

## Testing without a phone

The mock device mirrors the whole API, so suites run in CI on any machine.

```ts
import { mock } from "simwire";

const sms = mock();

await sms.send({ to: "+15550100000", text: "OTP: 1234" });
expect(sms.outbox).toHaveLength(1);

sms.simulateIncoming({ from: "+15550100001", text: "STOP" });
expect(sms.inbox).toHaveLength(1);

sms.failNext("no signal");                         // next send fails
```

## CLI

| Command | What it does |
| --- | --- |
| `simwire pair` | Shows the pairing QR and waits for the phone |
| `simwire send <to> <text>` | Sends an SMS and streams its statuses |
| `simwire listen --forward <url>` | POSTs every incoming SMS to your local server |
| `simwire doctor` | Reports why a setup is not working, with fixes |

Every command accepts `--mock` to run against the built-in mock device.

## Requirements

Node.js 18+, an Android 8+ phone running the
[simwire app](https://simwire.machinal.agency/download.html), both on the same
network. Full documentation at
[simwire.machinal.agency/docs.html](https://simwire.machinal.agency/docs.html).

## Fair use

Built for development, testing and small transactional volume: OTPs, alerts,
payment confirmations. Carriers throttle and ban SIMs that spam, so this is not
a bulk-messaging tool.

## License

MIT
