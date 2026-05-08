package dev.isaacudy.udeploy

import kotlinx.serialization.Serializable

/**
 * The udeploy update manifest. Hosted at a stable URL of the consumer's
 * choosing (e.g. `https://updates.example.com/manifest.json`) and
 * fetched by the launcher on every run.
 *
 * Changes to this format are versioned via [schema] — old launchers
 * tolerate unknown future fields by ignoring them, but a manifest with
 * a newer required schema should be rejected so a buggy launcher never
 * acts on a manifest it doesn't understand.
 *
 * Signed separately. The launcher fetches `manifest.json` and a
 * detached signature `manifest.json.sig` (Ed25519 over the canonical
 * UTF-8 bytes of `manifest.json`); both must verify against the
 * launcher's embedded public key before any payload work is started.
 */
@Serializable
data class Manifest(
    /**
     * Version of this manifest format. Bumped when the protocol
     * changes incompatibly. Launchers MUST refuse manifests with a
     * [schema] greater than the highest version they were built to
     * understand.
     */
    val schema: Int = SCHEMA_VERSION,

    /**
     * Latest available version of the application. Compared against
     * the launcher's local record of "currently installed payload"
     * via simple lexicographic version-string comparison; consumers
     * should pick a versioning scheme (semver, calver) that sorts
     * correctly under that comparison.
     */
    val version: String,

    /**
     * RFC 3339 timestamp of when this manifest (and the payloads it
     * references) were published. Informational; not used by the
     * launcher's update logic.
     */
    val releasedAt: String,

    /**
     * Per-target payload references. Keys are platform identifiers
     * (see [Platform]); values describe how to download and verify the
     * payload archive for that target.
     */
    val platforms: Map<String, PayloadRef>,

    /**
     * Optional human-readable release notes (markdown allowed). The
     * launcher may surface these to the user when prompting for
     * restart-to-update.
     */
    val notes: String? = null,
) {
    companion object {
        /** Highest manifest schema version this build of udeploy understands. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/**
 * Pointer to a single payload archive on the update site, plus the
 * integrity metadata needed to verify it before extracting.
 */
@Serializable
data class PayloadRef(
    /**
     * Absolute URL to the payload archive. By convention a `.tar.zst`
     * containing the bundled JRE + application files. Launchers
     * download this byte-for-byte and verify against [sha256] before
     * extracting.
     */
    val url: String,

    /**
     * Hex-encoded SHA-256 of the archive's bytes. Used as a download-
     * integrity check. NOT a security boundary — the security boundary
     * is the Ed25519 signature over the manifest, which signs the
     * sha256 transitively.
     */
    val sha256: String,

    /** Size in bytes of the archive. Used for progress UI. */
    val size: Long,
)

/**
 * Identifiers for the supported launcher targets. The string form
 * (e.g. `"macos-arm64"`) is what appears as a key in
 * [Manifest.platforms]; the launcher resolves its own host at runtime
 * and looks up the matching [PayloadRef].
 */
enum class Platform(val id: String) {
    MacOsArm64("macos-arm64"),
    MacOsX64("macos-x64"),
    WindowsX64("windows-x64"),
    LinuxX64("linux-x64"),
    LinuxArm64("linux-arm64"),
    ;

    companion object {
        fun fromId(id: String): Platform? = entries.firstOrNull { it.id == id }
    }
}
