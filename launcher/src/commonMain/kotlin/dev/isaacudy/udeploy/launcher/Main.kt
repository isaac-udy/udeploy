package dev.isaacudy.udeploy.launcher

/**
 * Entry point for the launcher binary. The real lifecycle goes here
 * once the platform glue is in place:
 *
 *  1. Resolve the local install root and read the state file to find
 *     which payload version is currently selected.
 *  2. Spawn the JVM from `<root>/payload/current/...`.
 *  3. In parallel, fetch the signed manifest, verify the Ed25519
 *     signature against the public key embedded at build time, and
 *     download the new payload if available.
 *  4. Atomically swap the `current` pointer to the new payload for
 *     the next launch.
 *  5. If the spawned JVM exits abnormally within a short window, mark
 *     the new payload as bad and roll back.
 *
 * For now this is a stub so the binaries can be produced end-to-end
 * before any of the real logic lands.
 */
fun main(args: Array<String>) {
    println("udeploy launcher (stub) — args: ${args.joinToString(" ")}")
}
