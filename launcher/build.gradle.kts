plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Each native target produces an executable named `udeploy-launcher`.
    // Consumers don't depend on this module as a library — they run CI
    // that builds the binary for each OS, embeds their public key /
    // manifest URL at build time, and ships the resulting executable
    // alongside their payload.
    val nativeTargets = listOf(
        macosArm64(),
        macosX64(),
        linuxX64(),
        linuxArm64(),
        mingwX64(),
    )

    nativeTargets.forEach { target ->
        target.binaries {
            executable {
                entryPoint = "dev.isaacudy.udeploy.launcher.main"
                baseName = "udeploy-launcher"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
