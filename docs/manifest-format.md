# Manifest format (schema 1)

The manifest is the only thing the launcher reads from the network on
each launch. It declares the latest available application version and
points at signed payload archives.

## Files

Two sibling files live at the consumer-chosen update URL:

- `manifest.json` — the manifest body (JSON, UTF-8).
- `manifest.json.sig` — a 64-byte raw Ed25519 signature over the
  exact byte sequence of `manifest.json`.

The launcher fetches both and rejects the update if either is missing
or the signature doesn't verify against the embedded public key.

## Body

```json
{
  "schema": 1,
  "version": "1.4.0",
  "releasedAt": "2026-05-09T12:00:00Z",
  "platforms": {
    "macos-arm64": {
      "url": "https://updates.example.com/payload/1.4.0/macos-arm64.tar.zst",
      "sha256": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
      "size": 152340992
    },
    "macos-x64":   { "url": "...", "sha256": "...", "size": ... },
    "windows-x64": { "url": "...", "sha256": "...", "size": ... },
    "linux-x64":   { "url": "...", "sha256": "...", "size": ... }
  },
  "notes": "Fixes a crash on launch when no campaigns exist."
}
```

## Field reference

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `schema` | int | yes | Format version. Bump on incompatible changes. Launchers must reject `schema > MAX_KNOWN`. |
| `version` | string | yes | Application version. Compared lexicographically against the launcher's local "current" version. |
| `releasedAt` | RFC 3339 string | yes | Timestamp. Informational only. |
| `platforms` | map<string, PayloadRef> | yes | Per-target payload pointers. Keys are platform IDs (`macos-arm64`, `macos-x64`, `windows-x64`, `linux-x64`, `linux-arm64`). |
| `notes` | string | no | Markdown release notes for the user-facing "Restart to update" prompt. |

### PayloadRef

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `url` | string | yes | Absolute URL to the payload archive. |
| `sha256` | hex string | yes | SHA-256 of the archive bytes (download integrity). |
| `size` | int (bytes) | yes | Used to render a download progress bar. |

## Versioning guidance

- Pick a version scheme (semver, calver, or other monotonic) and stick
  with it. The launcher does no semantic interpretation — it just
  compares strings — so you must ensure your scheme orders newer >
  older lexicographically.
- Recommended: zero-padded semver (`1.04.0` rather than `1.4.0`) if you
  expect minor numbers to exceed 9. Or calver: `2026.05.09`.

## Signing

```sh
# One-time keypair generation
openssl genpkey -algorithm Ed25519 -out udeploy-private.pem
openssl pkey -in udeploy-private.pem -pubout -out udeploy-public.pem

# Per-release: sign the manifest after publishing payloads
openssl pkeyutl -sign \
  -inkey udeploy-private.pem \
  -rawin \
  -in manifest.json \
  -out manifest.json.sig
```

The 32 raw bytes of the public key (extract from the PEM) are baked
into the launcher binary at build time via a generated Kotlin
constant. The launcher uses those bytes plus the `manifest.json.sig`
to verify before acting.

## Forward compatibility

Launchers ignore unknown JSON fields. Adding a new optional field
(e.g. `minLauncherVersion`, `channel`) at the top level or in a
`PayloadRef` is a non-breaking change at schema 1.

Bumping `schema` to 2 means launchers built against schema 1 will
refuse the manifest entirely — only do this when fields' meanings
change incompatibly, never just to add new ones.
