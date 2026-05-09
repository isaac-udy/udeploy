#!/usr/bin/env bash
#
# Build the Windows x64 launcher .exe inside an Ubuntu container,
# cross-compiling via mingw-w64.
#
# This is the same approach our CI uses — apt-install mingw-w64,
# point the gradle build at it, and let K/N's bundled toolchain
# handle the link step. The output is a real native Windows .exe
# you can copy to a Windows machine and run.
#
# Output: launcher/build/bin/mingwX64/releaseExecutable/udeploy-launcher.exe
#
# Requires a Docker daemon. On macOS, the cheapest option is
# Colima:  `brew install colima && colima start --cpu 4 --memory 6`.
#
# End-user prerequisite on the Windows machine: the Microsoft Edge
# WebView2 Runtime. Win10/11 ship it; old setups can install the
# bootstrapper from
# https://developer.microsoft.com/en-us/microsoft-edge/webview2/.
#
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
    echo "docker command not found. Install Docker (or Colima/OrbStack), then re-run." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon not reachable. Start Docker Desktop / Colima / OrbStack first." >&2
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# --platform linux/amd64 forces x86_64 even on Apple Silicon hosts —
# K/N doesn't publish a linux-aarch64 host distribution, so the
# container must run x86_64 (Rosetta 2 emulation) to use K/N at all.
# Volumes are shared with build-linux.sh so the gradle and K/N caches
# warm up across both builds.
docker run --rm \
    --platform linux/amd64 \
    --volume "$REPO_ROOT":/work \
    --volume udeploy-gradle-cache:/root/.gradle \
    --volume udeploy-konan-cache:/root/.konan \
    --workdir /work \
    --env GRADLE_USER_HOME=/root/.gradle \
    ubuntu:24.04 \
    bash -c '
        set -euo pipefail
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        apt-get install -y -qq \
            openjdk-21-jdk-headless \
            mingw-w64 \
            build-essential \
            curl \
            unzip \
            ca-certificates
        ./gradlew :launcher:linkReleaseExecutableMingwX64
    '

OUT="$REPO_ROOT/launcher/build/bin/mingwX64/releaseExecutable/udeploy-launcher.exe"
if [[ ! -f "$OUT" ]]; then
    echo "build finished but expected output not found at $OUT" >&2
    exit 1
fi

echo
echo "✓ Windows x64 .exe built:"
echo "  $OUT"
echo
echo "Copy to a Windows machine and run. WebView2 Runtime must be"
echo "installed (Win10/11 ship it; older Windows or stripped-down"
echo "builds may need https://developer.microsoft.com/en-us/microsoft-edge/webview2/)."
