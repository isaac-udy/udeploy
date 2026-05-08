# udeploy

A reusable launcher and auto-update framework for JVM desktop applications.

> **Status**: Pre-alpha, under active development. Not yet usable. APIs and
> wire formats are unstable and may change without notice.

## What it is

A small native launcher (built with Kotlin/Native) that becomes the
executable users install and double-click. The launcher fetches signed
versioned payload directories from an update site you host, verifies them
against an Ed25519 public key embedded at build time, and atomically swaps
in new versions on next launch.

In short: the thing users install is the launcher. The thing they actually
run is whatever payload the launcher most recently fetched.

## Why

[Hydraulic Conveyor](https://hydraulic.dev) does this beautifully but is
commercial. [Sparkle](https://sparkle-project.org/) (macOS) and
[WinSparkle](https://winsparkle.org/) (Windows) are great but split-platform
and require native bridging from the JVM. udeploy aims to be the free,
Kotlin-native, cross-platform option for JVM desktop apps — particularly
[Compose Multiplatform Desktop](https://www.jetbrains.com/lp/compose-multiplatform/)
projects.

## Architecture (planned)

Two binaries on disk per install:

```
What users install (signed once, ~rarely changes):
  ArcaneArchivist.app/Contents/MacOS/launcher       (~5 MB Kotlin/Native)

What the launcher manages (versioned, swapped on update):
  ~/Library/Application Support/<app>/payload/
    1.3.7/
    1.4.0/
    current → 1.4.0     (atomic symlink/junction swap)
```

On every launch:

1. Launcher reads its local state, knows the current payload version.
2. Spawns the JVM from `payload/current/runtime/bin/java`.
3. In parallel: fetches the signed update manifest, downloads new payloads
   if available, verifies signatures, atomically swaps in the new version
   for next launch.
4. If a new payload fails to launch within N seconds, rolls back to the
   previous payload.

## What you bring

- An Apple Developer ID (for macOS Gatekeeper / notarization).
- A Windows code-signing certificate (OV or EV).
- An Ed25519 keypair you generate yourself and protect — the public key
  gets baked into your launcher binary at build time, the private key
  signs your manifests in CI.
- Static-file hosting for the update site (S3, GCS, GitHub Pages, your
  own CDN).

## What udeploy provides

- The launcher binary (cross-compiled for macOS, Windows, Linux).
- Manifest format + Ed25519 signature verification.
- Atomic payload swap + rollback-on-failed-launch.
- Build-time configuration (your manifest URL, your public key, your
  payload entry point).

## Modules

- **`:core`** — KMP module with manifest types, signature protocol, and
  shared utilities. JVM target for tooling, native target for the launcher.
- **`:launcher`** — Kotlin/Native module producing per-OS launcher binaries.

## Build host requirements

The launcher's webview cinterop is per-target. Each target requires a
matching toolchain on the build host; tasks for targets the host can't
build for are skipped, so local devs only need the toolchain for their
own host. CI builds the full matrix.

| Target | Host | Required toolchain |
|---|---|---|
| macOS arm64 / x64 | macOS | Xcode CLT (`clang++` from `xcode-select --install`) |
| Linux x64 | Linux | `apt install libgtk-3-dev libwebkit2gtk-4.1-dev pkg-config build-essential` |
| Windows x64 | macOS / Linux / Windows | `mingw-w64` cross-compiler. macOS: `brew install mingw-w64`. Debian: `apt install mingw-w64`. WebView2 SDK is fetched automatically. |
| Linux arm64 | — | Webview build not yet wired up; uses a stub Platform.kt. |

## Documentation

- [Architecture](docs/architecture.md) — how the launcher works and what it
  protects against.
- [Manifest format](docs/manifest-format.md) — wire format spec.

## License

[MIT](LICENSE).
