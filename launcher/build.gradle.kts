import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
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
// per-platform static library with the host's toolchain, and feed the
// result into K/N cinterop.
//
// macOS: clang++ with -framework WebKit -framework Cocoa
// Linux: g++ with $(pkg-config --cflags gtk+-3.0 webkit2gtk-4.1)
// Windows: mingw g++ with the WebView2 SDK NuGet package
//
// Per-target build tasks all exist, but each one's `onlyIf` skips it
// when the host can't compile for that target. A macOS host only
// builds the macOS static lib; CI runs the matrix to cover all three
// platforms.
// --------------------------------------------------------------------

val os: OperatingSystem = OperatingSystem.current()
val webviewVersion = "0.12.0"
val webviewBuildRoot: File = layout.buildDirectory.dir("webview").get().asFile
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

fun runProcessCapture(command: List<String>): String {
    val process = ProcessBuilder(command)
        .redirectErrorStream(false)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed (exit $exitCode): ${command.joinToString(" ")}")
    }
    return output
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

// --------------------------------------------------------------------
// WebView2 SDK
//
// Microsoft ships the WebView2 SDK as a NuGet package. A `.nupkg`
// file is just a zip with `build/native/include/` (headers) and
// `build/native/x64/WebView2LoaderStatic.lib`. We download a pinned
// release and extract it for the Windows build to consume.
// --------------------------------------------------------------------

val webView2SdkVersion = "1.0.2792.45"
val webView2SdkRoot: File = webviewBuildRoot.resolve("webview2-sdk-$webView2SdkVersion")
val webView2IncludeDir: File = webView2SdkRoot.resolve("build/native/include")
val webView2LibDir: File = webView2SdkRoot.resolve("build/native/x64")

val downloadWebView2Sdk by tasks.registering {
    description = "Download and extract Microsoft.Web.WebView2 NuGet package"
    val nupkg = webviewBuildRoot.resolve("microsoft.web.webview2.$webView2SdkVersion.nupkg")
    val sdkRoot = webView2SdkRoot
    val buildRoot = webviewBuildRoot
    val version = webView2SdkVersion
    outputs.dir(sdkRoot)
    onlyIf { !sdkRoot.exists() }
    doLast {
        buildRoot.mkdirs()
        runProcess(
            listOf(
                "curl", "-fsSL",
                "https://www.nuget.org/api/v2/package/Microsoft.Web.WebView2/$version",
                "-o", nupkg.path,
            )
        )
        sdkRoot.mkdirs()
        // .nupkg is just a zip; unzip into the SDK root.
        runProcess(listOf("unzip", "-q", "-o", nupkg.path, "-d", sdkRoot.path))
    }
}

// --------------------------------------------------------------------
// Per-platform webview static-library builds.
//
// Each task produces build/webview/lib/<target>/libwebview.a. Tasks
// are skipped (not failed) when the host can't build for that target
// — local devs only build their host's matching target; CI runs the
// matrix.
// --------------------------------------------------------------------

fun TaskContainer.registerBuildWebviewMacOS(targetId: String, arch: String) =
    register("buildWebview${targetId.toCamelCase()}") {
        description = "Compile webview as a static library for $targetId (clang++)"
        dependsOn(downloadWebview)
        val srcCc = webviewSourceDir.resolve("core/src/webview.cc")
        val includeDir = webviewSourceDir.resolve("core/include")
        val outDir = webviewBuildRoot.resolve("lib/$targetId")
        val outLib = outDir.resolve("libwebview.a")
        val objFile = webviewBuildRoot.resolve("obj/$targetId/webview.o")
        val archArg = arch

        outputs.file(outLib)
        onlyIf { os.isMacOsX && !outLib.exists() }

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

fun TaskContainer.registerBuildWebviewLinux(targetId: String) =
    register("buildWebview${targetId.toCamelCase()}") {
        description = "Compile webview as a static library for $targetId (g++ + pkg-config)"
        dependsOn(downloadWebview)
        val srcCc = webviewSourceDir.resolve("core/src/webview.cc")
        val includeDir = webviewSourceDir.resolve("core/include")
        val outDir = webviewBuildRoot.resolve("lib/$targetId")
        val outLib = outDir.resolve("libwebview.a")
        val objFile = webviewBuildRoot.resolve("obj/$targetId/webview.o")

        outputs.file(outLib)
        onlyIf { os.isLinux && !outLib.exists() }

        doLast {
            outDir.mkdirs()
            objFile.parentFile.mkdirs()
            // pkg-config gives us -I flags for gtk + webkit2gtk headers.
            // Required dev packages on Debian/Ubuntu:
            //   libgtk-3-dev libwebkit2gtk-4.1-dev pkg-config build-essential
            val pkgFlags = runProcessCapture(
                listOf("pkg-config", "--cflags", "gtk+-3.0", "webkit2gtk-4.1")
            ).split(" ").filter { it.isNotBlank() }
            runProcess(
                listOf("g++", "-std=c++14", "-fPIC", "-DWEBVIEW_STATIC") +
                    pkgFlags +
                    listOf(
                        "-I", includeDir.path,
                        "-c", srcCc.path,
                        "-o", objFile.path,
                    )
            )
            runProcess(listOf("ar", "rcs", outLib.path, objFile.path))
        }
    }

/**
 * Locate a mingw-w64 cross-compiler usable from the host. On macOS the
 * canonical install is `brew install mingw-w64`. On Linux distros it
 * varies (`mingw-w64`, `gcc-mingw-w64-x86-64`). On Windows native it's
 * the toolchain bundled with msys2/mingw. Returns null if not found —
 * the caller surfaces a friendly error pointing at the install
 * instructions.
 */
fun findMingwTool(name: String): File? {
    val candidates = sequence {
        // PATH lookup via `which`.
        try {
            val process = ProcessBuilder("which", name).redirectErrorStream(false).start()
            process.waitFor()
            val out = process.inputStream.bufferedReader().readText().trim()
            if (out.isNotBlank()) yield(File(out))
        } catch (_: Throwable) {}
        // Common Homebrew install paths.
        yield(File("/opt/homebrew/bin/$name"))
        yield(File("/usr/local/bin/$name"))
    }
    return candidates.firstOrNull { it.exists() && it.canExecute() }
}

fun TaskContainer.registerBuildWebviewMingw(targetId: String) =
    register("buildWebview${targetId.toCamelCase()}") {
        description = "Compile webview as a static library for $targetId (mingw + WebView2 SDK)"
        dependsOn(downloadWebview)
        dependsOn(downloadWebView2Sdk)

        val srcCc = webviewSourceDir.resolve("core/src/webview.cc")
        val includeDir = webviewSourceDir.resolve("core/include")
        val webview2Inc = webView2IncludeDir
        val outDir = webviewBuildRoot.resolve("lib/$targetId")
        val outLib = outDir.resolve("libwebview.a")
        val objFile = webviewBuildRoot.resolve("obj/$targetId/webview.o")
        val stubCc = webviewBuildRoot.resolve("obj/$targetId/abi_stubs.cc")
        val stubObj = webviewBuildRoot.resolve("obj/$targetId/abi_stubs.o")

        outputs.file(outLib)
        onlyIf { !outLib.exists() }

        doLast {
            val gxx = findMingwTool("x86_64-w64-mingw32-g++")
                ?: error(
                    "x86_64-w64-mingw32-g++ not found. Install mingw-w64:\n" +
                        "  macOS:   brew install mingw-w64\n" +
                        "  Debian:  sudo apt-get install mingw-w64\n" +
                        "  Windows: install via msys2 (pacman -S mingw-w64-x86_64-toolchain)"
                )
            val ar = findMingwTool("x86_64-w64-mingw32-ar")
                ?: error("x86_64-w64-mingw32-ar not found alongside g++ — broken mingw install?")

            outDir.mkdirs()
            objFile.parentFile.mkdirs()

            runProcess(
                listOf(
                    gxx.path,
                    "-std=c++14",
                    "-DWEBVIEW_STATIC",
                    "-I", includeDir.path,
                    "-I", webview2Inc.path,
                    "-c", srcCc.path,
                    "-o", objFile.path,
                )
            )

            // ABI compatibility stub. The host's mingw libstdc++
            // (e.g. brew's GCC 14) instantiates allocator templates
            // that reference modern symbols not present in K/N's
            // bundled mingw libstdc++ (older GCC). The two we've
            // observed missing are below; provide stubs that just
            // abort if ever called (they're only hit on integer-
            // overflow allocation paths, which sane code doesn't
            // reach). Compiled into the same archive as webview.o
            // so the K/N link sees them as already-defined.
            stubCc.writeText(
                """
                #include <cstdlib>
                #include <exception>

                namespace std {
                    [[noreturn]] void __throw_bad_array_new_length() { std::abort(); }
                    [[noreturn]] void __glibcxx_assert_fail(const char*, int, const char*, const char*) noexcept {
                        std::abort();
                    }
                }
                """.trimIndent()
            )
            runProcess(
                listOf(
                    gxx.path,
                    "-std=c++14",
                    "-c", stubCc.path,
                    "-o", stubObj.path,
                )
            )
            runProcess(listOf(ar.path, "rcs", outLib.path, objFile.path, stubObj.path))
        }
    }

val buildWebviewMacosArm64 = tasks.registerBuildWebviewMacOS("macos-arm64", "arm64")
val buildWebviewMacosX64 = tasks.registerBuildWebviewMacOS("macos-x64", "x86_64")
val buildWebviewLinuxX64 = tasks.registerBuildWebviewLinux("linux-x64")
val buildWebviewMingwX64 = tasks.registerBuildWebviewMingw("mingw-x64")

// --------------------------------------------------------------------
// macOS .app bundle
//
// Wraps the linked Kotlin/Native executable in a minimal .app
// directory so it's double-clickable from Finder. Not signed or
// notarized — that's a downstream consumer concern.
// --------------------------------------------------------------------

val packageMacosArm64App by tasks.registering {
    description = "Wrap the macOS arm64 launcher binary in a .app bundle"
    dependsOn("linkReleaseExecutableMacosArm64")
    val nativeOutputDir = layout.buildDirectory.dir("bin/macosArm64/releaseExecutable").get().asFile
    val executable = nativeOutputDir.resolve("udeploy-launcher.kexe")
    val appDir = layout.buildDirectory.dir("packagedApp/macosArm64/udeploy-launcher.app").get().asFile

    inputs.file(executable)
    outputs.dir(appDir)

    doLast {
        val contentsDir = appDir.resolve("Contents")
        val macosDir = contentsDir.resolve("MacOS")
        appDir.deleteRecursively()
        macosDir.mkdirs()

        executable.copyTo(macosDir.resolve("udeploy-launcher"), overwrite = true)
        macosDir.resolve("udeploy-launcher").setExecutable(true)

        contentsDir.resolve("Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>            <string>udeploy-launcher</string>
                <key>CFBundleDisplayName</key>     <string>udeploy launcher</string>
                <key>CFBundleIdentifier</key>      <string>dev.isaacudy.udeploy.launcher</string>
                <key>CFBundleVersion</key>         <string>0.1.0</string>
                <key>CFBundleShortVersionString</key><string>0.1.0</string>
                <key>CFBundlePackageType</key>     <string>APPL</string>
                <key>CFBundleExecutable</key>      <string>udeploy-launcher</string>
                <key>LSMinimumSystemVersion</key>  <string>11.0</string>
                <key>NSHighResolutionCapable</key> <true/>
            </dict>
            </plist>
            """.trimIndent()
        )

        println("Built ${appDir.path}")
    }
}

// --------------------------------------------------------------------
// Kotlin/Native source set + cinterop wiring
// --------------------------------------------------------------------

fun String.toCamelCase(): String =
    split('-').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

fun KotlinNativeTarget.configureWebviewCinterop(
    targetId: String,
    buildTaskProvider: TaskProvider<*>,
) {
    val staticLib = webviewBuildRoot.resolve("lib/$targetId/libwebview.a")
    compilations.named("main").configure {
        cinterops.create("webview") {
            defFile(project.file("src/nativeInterop/cinterop/webview.def"))
            includeDirs(webviewSourceDir.resolve("core/include"))
        }
    }
    // Static library is supplied at link time (not at cinterop time)
    // so on hosts that can't build for this target, cinterop binding
    // generation still succeeds — only the link step fails.
    binaries.all {
        if (this is org.jetbrains.kotlin.gradle.plugin.mpp.Executable) {
            linkTaskProvider.configure { dependsOn(buildTaskProvider) }
            linkerOpts.add("-L${staticLib.parentFile.path}")
            linkerOpts.add("-lwebview")
        }
    }
}

/**
 * Mingw variant of [configureWebviewCinterop] — additionally wires up
 * the WebView2 SDK include path (so the cinterop binding generation
 * can resolve any platform-specific declarations the headers expose
 * on Windows) and the WebView2LoaderStatic.lib library path so the
 * link step can resolve the COM-loader symbols.
 */
fun KotlinNativeTarget.configureWebviewCinteropMingw(
    targetId: String,
    buildTaskProvider: TaskProvider<*>,
) {
    val staticLib = webviewBuildRoot.resolve("lib/$targetId/libwebview.a")
    compilations.named("main").configure {
        cinterops.create("webview") {
            defFile(project.file("src/nativeInterop/cinterop/webview.def"))
            includeDirs(
                webviewSourceDir.resolve("core/include"),
                webView2IncludeDir,
            )
        }
    }
    binaries.all {
        if (this is org.jetbrains.kotlin.gradle.plugin.mpp.Executable) {
            linkTaskProvider.configure {
                dependsOn(buildTaskProvider)
                dependsOn(downloadWebView2Sdk)
            }
            linkerOpts.add("-L${staticLib.parentFile.path}")
            linkerOpts.add("-L${webView2LibDir.path}")
            linkerOpts.add("-lwebview")
            // WebView2LoaderStatic.lib ships as MSVC COFF; mingw can
            // link it directly via -l:filename.
            linkerOpts.add("-l:WebView2LoaderStatic.lib")
        }
    }
}

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

    macosArm64 { configureWebviewCinterop("macos-arm64", buildWebviewMacosArm64) }
    macosX64 { configureWebviewCinterop("macos-x64", buildWebviewMacosX64) }
    linuxX64 { configureWebviewCinterop("linux-x64", buildWebviewLinuxX64) }
    mingwX64 { configureWebviewCinteropMingw("mingw-x64", buildWebviewMingwX64) }
    // linuxArm64 still uses a standalone stub Platform.kt — the
    // webview build for that target hasn't been wired up yet.

    // Shared source set for targets that have a webview cinterop. The
    // launcher's UI code is identical across them (same C API). Only
    // linuxArm64 is excluded — it falls back to commonMain stub.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }

        val webviewMain by creating {
            dependsOn(commonMain.get())
        }
        macosArm64Main.get().dependsOn(webviewMain)
        macosX64Main.get().dependsOn(webviewMain)
        linuxX64Main.get().dependsOn(webviewMain)
        mingwX64Main.get().dependsOn(webviewMain)
    }
}
