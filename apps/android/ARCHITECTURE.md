# simwire Android app — architecture

The phone is the gateway. This app has one job: stay alive, move SMS in both
directions, and be honest about its own health. It implements the device side
of [`packages/protocol`](../../packages/protocol/PROTOCOL.md).

## Stack

- Kotlin, Jetpack Compose (Material 3, dark-first theme matching the brand)
- Ktor embedded server (WebSocket endpoint `/ws`, port 4650)
- Room for the durable message queue
- NSD (`NsdManager`) to advertise `_simwire._tcp` on the LAN
- CameraX + ML Kit for the pairing QR scanner
- No Firebase, no analytics, no network calls off the LAN

## Modules

```
app/
  core/
    server/      Ktor WS server, frame codec, session auth (bearer token)
    sms/         SmsSender (SmsManager, multipart, dual-SIM via SubscriptionManager)
                 SmsObserver (BroadcastReceiver SMS_RECEIVED + sent/delivered PendingIntents)
    queue/       Room entities + OutboxWorker (retry with backoff, survives restarts)
    pairing/     QR scan -> POST PairingRequest to the CLI endpoint, token store
    health/      Battery optimization status, OEM kill-list detection, permission audit
  ui/
    HomeScreen      connection state, live message journal
    PairScreen      full-screen QR scanner
    HealthScreen    actionable checklist (permissions, battery, network)
```

## Lifecycle model (the hard part)

- A **foreground service** hosts the Ktor server and the SMS observers.
  Started on boot (`BOOT_COMPLETED`) once pairing exists.
- Every outgoing message is **persisted to Room before** the `queued` status
  frame is emitted — the contract the SDK relies on.
- Incoming SMS while no client is connected are buffered in Room and replayed
  on the next `hello.ack` (see PROTOCOL.md).
- `health/` maps the device brand to known battery-killer settings
  (dontkillmyapp.com data) and deep-links the user to the right settings
  screen. Surfaced in-app and to `simwire doctor`.

## Status mapping

| Android signal                          | Protocol frame                      |
| --------------------------------------- | ----------------------------------- |
| Room insert OK                           | `message.status: queued`            |
| `SENT` PendingIntent result OK           | `message.status: sent`              |
| `DELIVERED` PendingIntent result OK      | `message.status: delivered`         |
| Any failure / timeout after retries      | `message.status: failed` + `error`  |
| `SMS_RECEIVED` broadcast                 | `message.incoming`                  |
| Battery/connectivity callbacks (30s)     | `device.state`                      |

## Play Store note

SMS permissions (`SEND_SMS`, `RECEIVE_SMS`) put the app in Play's restricted
"default SMS handler" policy territory. Plan A: distribute the APK directly +
F-Droid (like capcom6 does). Plan B (later): Play listing under the
device-automation exception with a declaration form. Do not block v1 on Play.

## Phase 1 scope

Pairing + send + receive + durable queue + foreground service + the three
screens. mDNS advertise. No encryption-at-rest, no multi-client fan-out yet
(single paired client), no MMS.
