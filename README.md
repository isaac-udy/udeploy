# udeploy

A reusable launcher and auto-update framework for JVM desktop applications,
in the spirit of Sparkle / Conveyor but free, open-source, and Kotlin-native.

## Status

Pre-alpha — not yet usable. This README is a placeholder while the project
is being scaffolded.

## What it will be

- A small native launcher (Kotlin/Native) that ships as the desktop binary
  users install and double-click.
- A versioned-payload model: the launcher fetches signed payload directories
  on update, verifies them against an embedded public key, and atomically
  swaps in the new version on next launch.
- Cross-platform: macOS (.app), Windows (.exe), Linux (AppImage / tarball).
- BYO code-signing certs — udeploy verifies its own Ed25519 signatures on
  payloads independently of OS-level signing.

## License

MIT.
