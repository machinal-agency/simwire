# Contributing

Thanks for being here. Bug reports from real setups are as valuable as code:
this project depends on how phones behave in the wild, and no two Android
manufacturers agree on what "keep this service alive" means.

## Reporting a bug

Open an issue with the template. It asks for the output of `npx simwire doctor`
because most reports come down to something that command already checks:
pairing, discovery, reachability, permissions, battery optimization. Pasting it
usually saves a full round trip.

For anything security-related, do not open an issue: see [SECURITY.md](SECURITY.md).

## Getting set up

```bash
pnpm install
pnpm build
pnpm test
```

You can exercise the whole CLI without a phone:

```bash
node packages/sdk/dist/cli/index.js send "+15550100000" "hello" --mock
node packages/sdk/dist/cli/index.js listen --mock
```

The Android app opens in Android Studio from `apps/android`; see
[its README](apps/android/README.md). The site is an Astro project in
`apps/web`.

## Working on the SDK

Anything touching the session lifecycle should come with a test against the
mock device. `simulateDrop()` covers reconnection, `failNext()` covers carrier
failures, and `simulateIncoming()` covers the receive path, so most behaviour
can be pinned down without hardware.

If you change the frames exchanged between the phone and a client, update
[PROTOCOL.md](packages/protocol/PROTOCOL.md) and the Kotlin types in
`apps/android/.../core/protocol/Frames.kt` in the same pull request. The two
sides are hand-kept in sync; a mismatch fails at runtime, not at build time.

## Style

Match the code around you. The codebase is written in English, comments explain
why rather than what, and there are few of them on purpose. Tests state the
behaviour they protect in their name.

Commits follow [Conventional Commits](https://www.conventionalcommits.org)
(`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`), with a body explaining the
reasoning when it is not obvious from the diff.

## Pull requests

Keep them focused: one concern per pull request is much easier to review than a
sweep. CI runs the SDK build and tests plus an Android build; both need to be
green. If your change alters what users see, update the docs page in
`apps/web/src/pages/docs.astro` too.
