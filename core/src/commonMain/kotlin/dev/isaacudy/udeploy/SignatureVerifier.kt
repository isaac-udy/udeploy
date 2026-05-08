package dev.isaacudy.udeploy

/**
 * Verifies an Ed25519 signature over arbitrary bytes against a
 * fixed public key. The launcher embeds the consumer-app's public
 * key at build time (via a build-config injection step) and uses
 * this interface to validate manifests and payload metadata before
 * acting on them.
 *
 * Implementations live per-target — the JVM uses Java's built-in
 * `java.security` provider; native targets use a small Ed25519
 * implementation (TBD: pure Kotlin port vs. libsodium cinterop).
 */
interface SignatureVerifier {
    /**
     * @param publicKey 32-byte raw Ed25519 public key.
     * @param message the bytes that were signed.
     * @param signature 64-byte raw Ed25519 signature.
     * @return true iff [signature] is a valid Ed25519 signature of
     *   [message] under [publicKey].
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}
