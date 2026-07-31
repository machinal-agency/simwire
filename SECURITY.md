# Security

simwire holds a pairing token and can read and send your text messages, so
security reports matter here more than in most projects. Thank you for taking
the time.

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private reporting instead:

> [Report a vulnerability](https://github.com/machinal-agency/simwire/security/advisories/new)

That opens a private thread visible only to the maintainers. Please include:

- what an attacker can do, and what they need first (same network? the token?
  physical access to the phone?)
- the versions you tested (`npx simwire --version`, the app version from the
  Play-store-less APK, your Android release)
- steps to reproduce, ideally with a minimal script

Expect a first reply within 72 hours. If a fix is needed, we will agree on a
disclosure date with you and credit you in the advisory unless you prefer
otherwise.

## What is in scope

The npm package, the Android app, and the pairing protocol in
[`packages/protocol`](packages/protocol/PROTOCOL.md).

## The security model, stated plainly

simwire deliberately trades some hardening for zero configuration. Knowing
where the lines are drawn helps you judge whether a finding is a bug or a
documented limitation.

**Trust boundary is your local network.** The gateway listens on the LAN and
authenticates clients with a bearer token generated on the phone. Anyone who
holds that token and can reach the phone can send and read messages through it.

**Traffic is not encrypted.** Frames travel over a plain WebSocket, and the
pairing callback is plain HTTP. Someone able to observe traffic on your network
can read the content of your messages. This is why the app is meant for a
network you control, and why we do not recommend pairing over public Wi-Fi. If
you need the phone reachable from elsewhere, put both ends on a
[Tailscale](https://tailscale.com) network rather than forwarding a port.

**The token is stored unencrypted** in the app's private storage, which Android
isolates from other apps. A rooted device, or one with an unlocked bootloader,
gives it up.

**Release builds are not debuggable**, which is why the APKs on the releases
page are signed release builds rather than debug ones.

Reports that improve any of these, especially a practical path to encrypted
transport that keeps pairing a single QR scan, are very welcome.

## What is not a vulnerability

- Carriers rate-limiting or banning a SIM used for bulk sending. simwire is
  built for development, testing and small transactional volume; sending spam
  is against every carrier's terms and is not a supported use.
- Someone with physical access to an unlocked phone unpairing or reading
  messages. That is the phone's lock screen doing its job, not ours.
