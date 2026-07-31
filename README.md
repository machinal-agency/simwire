# simwire

Send and receive real SMS from Node.js through your own Android phone.
No Twilio, no signup, no per-message fees — your SIM is the gateway.

```bash
npm install simwire
npx simwire pair        # scan the QR with the simwire Android app
```

```ts
import { connect } from "simwire";

const sms = await connect();
const msg = await sms.send({ to: "+15550142834", text: "Your code: 4821" });
await msg.waitForDelivery();

sms.on("message", (m) => console.log(m.from, m.text));
```

No phone around? The mock device mirrors the whole API for tests and CI:

```ts
import { mock } from "simwire";

const sms = mock();
await sms.send({ to: "+15550100000", text: "OTP: 1234" });
sms.simulateIncoming({ from: "+15550100001", text: "STOP" });
expect(sms.outbox).toHaveLength(1);
```

## Repository layout

| Path                 | What it is                                                        |
| -------------------- | ----------------------------------------------------------------- |
| `packages/sdk`       | The `simwire` npm package: TypeScript SDK + CLI (`pair`, `send`, `listen`, `doctor`) |
| `packages/protocol`  | Shared wire protocol types + [PROTOCOL.md](packages/protocol/PROTOCOL.md) |
| `apps/android`       | Android gateway app, phase 1 code complete ([build guide](apps/android/README.md), [architecture](apps/android/ARCHITECTURE.md)) |
| `apps/web`           | Astro site behind [simwire.machinal.agency](https://simwire.machinal.agency): landing, docs and app download |

## Development

```bash
pnpm install
pnpm build
pnpm test
```

Try the CLI without a phone:

```bash
node packages/sdk/dist/cli/index.js send "+15550100000" "hello" --mock
node packages/sdk/dist/cli/index.js listen --mock
```

## Positioning

Built for development, testing and small transactional volume — OTPs, alerts,
payment confirmations. Not a bulk-messaging tool: carriers throttle and ban
SIMs that spam.

## License

MIT
