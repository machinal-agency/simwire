# simwire wire protocol (v1)

Contract between the Android app (the gateway) and any client (SDK, CLI).

## Roles

- **Device** (Android app): runs a WebSocket server on the LAN, sends/receives real SMS.
- **Client** (SDK/CLI): connects to the device, submits messages, receives events.

## Discovery

The device advertises `_simwire._tcp` over mDNS with its port (default `4650`).
Clients may also connect to an explicit `host:port` (fallback when mDNS is
blocked, e.g. guest Wi-Fi with client isolation).

## Pairing (one-time)

1. `simwire pair` starts a short-lived HTTP endpoint on the dev machine and
   renders a QR code containing `PairingQrPayload` (host, port, one-time code).
2. The user scans the QR from the Android app.
3. The app generates a bearer `token`, then POSTs a `PairingRequest` to the
   CLI endpoint: the one-time code, its own `endpoint` (host:port), device
   info, and the token.
4. The CLI stores `{endpoint, token}` in `~/.simwire/config.json` and responds
   with `PairingResponse`.

The token never transits through any third-party server. Rotating it from the
app invalidates all clients.

## Session

Transport: WebSocket, JSON text frames, one object per frame.

1. Client connects to `ws://<device>:<port>/ws` and MUST send `hello` first.
2. Device replies `hello.ack` (with `DeviceInfo`) or `error{unauthorized}` and
   closes.
3. Then, in any order:
   - client -> `send` — enqueue an SMS. Device answers with a stream of
     `message.status` frames for that id: `queued` -> `sent` -> `delivered`
     (or `failed` with `error`).
   - device -> `message.incoming` — a real SMS arrived on the SIM.
   - device -> `device.state` — battery/network heartbeat (every 30s).
   - `ping`/`pong` — liveness (client pings every 15s).

## Delivery guarantees

- `send` is durably queued on the device (Room) before `queued` is emitted:
  a killed app resumes the queue on restart.
- Status frames for a given id are monotonic; `delivered` may never arrive
  (carrier-dependent) — clients should treat `sent` as success after a
  configurable timeout.
- Incoming messages received while no client is connected are buffered on the
  device and replayed on the next `hello.ack`.
