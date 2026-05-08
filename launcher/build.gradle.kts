import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// --------------------------------------------------------------------
// webview vendoring
//
// Upstream webview ships source-only (single .h + single .cc) and is
// MIT-licensed. We download a pinned release tarball, compile it to a
// per-platform static library with `clang++` directly (no CMake
// dependency), and feed the result into K/N cinterop.
// --------------------------------------------------------------------

val webviewVersion = "0.12.0"
val webviewBuildRoot: File = layout.buildDirectory.dir("webview").get().asFile
// tar xzf extracts the tarball's top-level dir directly into webviewBuildRoot
// (no nested `source/` segment), so the source dir is just the version name.
val webviewSourceDir: File = webviewBuildRoot.resolve("webview-$webviewVersion")

fun runProcess(command: List<String>, workingDir: File? = null) {
    val process = ProcessBuilder(command)
        .apply { if (workingDir != null) directory(workingDir) }
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed (exit $exitCode): ${command.joinToString(" ")}\n$output")
    }
}

val downloadWebview by tasks.registering {
    description = "Download and extract upstream webview source"
    val tarball = webviewBuildRoot.resolve("webview-$webviewVersion.tar.gz")
    val sourceDir = webviewSourceDir
    val buildRoot = webviewBuildRoot
    val version = webviewVersion
    outputs.dir(sourceDir)
    onlyIf { !sourceDir.exists() }
    doLast {
        buildRoot.mkdirs()
        runProcess(
            listOf(
                "curl", "-fsSL",
                "https://github.com/webview/webview/archive/refs/tags/$version.tar.gz",
                "-o", tarball.path,
            )
        )
        runProcess(listOf("tar", "xzf", tarball.path), workingDir = buildRoot)
    }
}

fun TaskContainer.registerBuildWebview(target: String, arch: String) =
    register("buildWebview${target.replace('-', '_').replaceFirstChar { it.uppercase() }}") {
        description = "Compile webview as a static library for $target"
        dependsOn(downloadWebview)
        val srcCc = webviewSourceDir.resolve("core/src/webview.cc")
        val includeDir = webviewSourceDir.resolve("core/include")
        val outDir = webviewBuildRoot.resolve("lib/$target")
        val outLib = outDir.resolve("libwebview.a")
        val objFile = webviewBuildRoot.resolve("obj/$target/webview.o")
        val archArg = arch

        // No `inputs.dir(...)` — the source dir doesn't exist until
        // downloadWebview runs, and Gradle validates inputs at graph
        // time. We rely on `onlyIf { !outLib.exists() }` for skip-when-
        // already-built; bumping `webviewVersion` forces a rebuild via
        // the changed source-dir name.
        outputs.file(outLib)
        onlyIf { !outLib.exists() }

        doLast {
            outDir.mkdirs()
            objFile.parentFile.mkdirs()
            runProcess(
                listOf(
                    "clang++", "-std=c++14", "-arch", archArg,
                    "-DWEBVIEW_STATIC",
                    "-I", includeDir.path,
                    "-c", srcCc.path,
                    "-o", objFile.path,
                )
            )
            runProcess(listOf("ar", "rcs", outLib.path, objFile.path))
        }
    }

val buildWebviewMacosArm64 = tasks.registerBuildWebview("macos-arm64", "arm64")

kotlin {
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

    macosArm64 {
        compilations.named("main").configure {
            cinterops.create("webview") {
                defFile(project.file("src/nativeInterop/cinterop/webview.def"))
                includeDirs(webviewSourceDir.resolve("core/include"))
                extraOpts(
                    "-libraryPath", webviewBuildRoot.resolve("lib/macos-arm64").path,
                    "-staticLibrary", "libwebview.a",
                )
                tasks.named(interopProcessingTaskName) {
                    dependsOn(buildWebviewMacosArm64)
                }
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
