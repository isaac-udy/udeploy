import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Native targets for the launcher binary. Add more as we expand
    // platform support; the host that runs `./gradlew build` will only
    // be able to build its own host's targets, but the project still
    // declares them all so cross-platform CI can pick them up.
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
