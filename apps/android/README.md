# simwire Android app

The phone side of simwire: pairs with the CLI over a QR code, then runs a
foreground service hosting the WebSocket gateway (`:4650`) that sends and
receives real SMS. Protocol details in
[../../packages/protocol/PROTOCOL.md](../../packages/protocol/PROTOCOL.md),
design in [ARCHITECTURE.md](ARCHITECTURE.md).

## Build

Open `apps/android` in **Android Studio** (Ladybug or newer). It provisions
Gradle from the wrapper properties automatically, then:

1. Let the Gradle sync finish (first sync downloads dependencies).
2. Plug your phone in with USB debugging on, press **Run**.

Command line alternative once the Android SDK is installed:

```bash
cd apps/android
gradle wrapper        # one-time, generates the wrapper jar
./gradlew :app:installDebug
```

## First end-to-end SMS

1. On the phone: open simwire, go through **Health** until all checks are green.
2. On your computer: `npx simwire pair`, scan the QR from the app.
3. `npx simwire send "+15550142834" "hello from my own gateway"`
4. Watch the journal on the phone and the statuses in your terminal.

## Phase 1 scope shipped here

- QR pairing against the CLI's one-shot endpoint (token generated on-device)
- Ktor WebSocket server with hello auth, single client, buffered replay
- Durable Room outbox: queued → sending → sent → delivered/failed, retries
- Incoming SMS capture (multipart merged), buffered when no client is online
- Dual-SIM send via `simSlot`, carrier list in `hello.ack`
- mDNS advertising (`_simwire._tcp`)
- Foreground service, boot autostart, battery-optimization guidance in Health

Not yet: MMS, multi-client fan-out, encrypted transport (LAN-only trust),
Play Store packaging.
