# Architecture

## The two-tier model

udeploy splits a "desktop app" into two artefacts:

1. **The launcher** — a tiny native binary (~5 MB Kotlin/Native) that the
   user installs and double-clicks. Signed with the consumer's OS
   code-signing certs. Almost never changes after first ship.
2. **The payload** — the actual JVM-based application: a bundled JRE +
   the consumer's jars + resources. Versioned. Swapped on update.

The launcher is what the OS thinks the app is. The payload is what the
app is to the user. The launcher's only job is finding the right payload
and running it.

## Disk layout

```
<install-root>/                            (set by the platform installer)
  launcher                                 (the executable; in /Applications/X.app on macOS,
                                            in Program Files on Windows, etc.)

<state-root>/                              (per-user data; OS-specific)
  payload/
    1.3.7/
      runtime/                             (bundled JRE)
      lib/                                 (consumer's jars)
      resources/
    1.4.0/
      runtime/
      lib/
      resources/
    current → 1.4.0                        (atomically-swapped pointer)
  state.json                               (manifest cache, version pointer, failure counter)
```

Each platform has a different `<state-root>`:

- macOS: `~/Library/Application Support/<app-id>/`
- Windows: `%LOCALAPPDATA%\<app-id>\`
- Linux: `${XDG_DATA_HOME:-~/.local/share}/<app-id>/`

## Lifecycle (every launch)

```
1. Read state.json → know current payload version V.
2. Fork: spawn payload V's JVM with the consumer's main class.
   The launcher returns to monitoring duty; user sees the app.
3. In parallel:
     - Fetch manifest.json and manifest.json.sig from the update URL.
     - Verify the Ed25519 signature against the embedded public key.
     - Reject the manifest if its `schema` is newer than the launcher
       was built to understand.
     - If manifest.version > V:
         - Look up PayloadRef for this host's Platform.
         - Download archive to <state-root>/payload/<new>.tmp.tar.zst
         - Verify SHA-256 against PayloadRef.sha256.
         - Extract to <state-root>/payload/<new>.tmp/
         - Atomically rename <new>.tmp/ → <new>/
         - Update state.json's "next-launch-target" to <new>.
4. On clean exit: nothing to do — next launch reads state.json and
   spawns <new>.
5. On crash within first N seconds:
     - Increment failure counter for V (or for "next-launch-target"
       if a swap happened).
     - After ≥3 failures, mark that version bad and revert
       state.json's "next-launch-target" to the previous good
       version.
```

## Threat model

What udeploy protects against:

- **Compromised CDN / DNS hijack / TLS misissuance.** An attacker
  controlling the update URL cannot push a malicious payload because
  the manifest must be signed with the consumer's Ed25519 private
  key. Without that key, no signature verifies, and the launcher
  refuses to act.
- **Tampered downloads.** SHA-256 of the payload is signed
  transitively (it's a field in the signed manifest). A flipped bit
  fails verification before extraction.
- **Buggy new payloads.** Rollback-on-failed-launch reverts to the
  previous good version after N consecutive crashes.

What udeploy does NOT protect against:

- **A compromised developer machine that can sign new manifests.**
  If the Ed25519 private key is stolen, the attacker IS the
  developer for update purposes. Treat the key like an SSH key —
  ideally on a hardware token, at minimum encrypted at rest with
  a passphrase only used in CI secrets.
- **A compromised launcher binary on the user's disk.** Once the
  launcher is on disk, udeploy has no way to detect that it's been
  swapped out from under the user. Rely on OS-level code-signing
  (Apple Gatekeeper, Windows Authenticode) for that surface.
- **Downgrade attacks.** A signed manifest with an older `version`
  field is, by default, accepted as a current truth — udeploy
  doesn't enforce monotonically-increasing versions across fetches.
  This is intentional (lets you roll back centrally if a release
  is bad) but means a stolen old-but-signed manifest could pin
  users to a known-vulnerable version. Not yet mitigated;
  candidate for a future "minimum version" field.

## Why Ed25519, not OS code-signing for updates

OS code-signing answers "did this binary come from someone with this
cert?" — but cert-issuance authorities have been compromised (or
mis-issued) at non-trivial rates over the years. A consumer-controlled
Ed25519 key in addition to OS signing means udeploy still verifies
even if Apple's or Microsoft's CA chain is the weak link. It also
lets us verify *payloads* that aren't whole-app bundles (jars, native
libs inside the JRE) — those don't fit cleanly into platform code-
signing frameworks anyway.

The OS code-signing on the launcher itself is still essential — that's
what gets past Gatekeeper and SmartScreen on first install. udeploy
verifies *updates*, not the initial install.
