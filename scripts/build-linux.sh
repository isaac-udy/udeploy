#!/usr/bin/env bash
#
# Build the Linux x64 launcher binary inside an Ubuntu container.
#
# Useful for testing the Linux build from a macOS host without
# setting up a full GTK / WebKit2GTK cross-toolchain locally — the
# container has a real Linux apt environment and produces a binary
# that runs on any reasonably-recent Ubuntu / Debian / Fedora install.
#
# Output: launcher/build/bin/linuxX64/releaseExecutable/udeploy-launcher.kexe
#
# Requires a Docker daemon. On macOS, the cheapest option is
# Colima:  `brew install colima && colima start --cpu 4 --memory 6`.
#
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
    cat <<'EOF' >&2
docker command not found. Install Docker (or any compatible runtime),
then re-run this script.

Recommended on macOS:
  brew install colima
  colima start --cpu 4 --memory 6

Other options: Docker Desktop, OrbStack.
EOF
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    cat <<'EOF' >&2
docker CLI is installed, but the daemon isn't reachable. If you have
Colima / Docker Desktop / OrbStack installed, start it first.

If you don't have a runtime yet:
  brew install colima
  colima start --cpu 4 --memory 6
EOF
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Named volumes so the gradle + konan caches persist across runs.
# First build is ~5-7 minutes (downloading apt packages + Kotlin/Native
# distribution); subsequent builds are ~30 seconds.
docker run --rm \
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
            libgtk-3-dev \
            libwebkit2gtk-4.1-dev \
            pkg-config \
            build-essential \
            curl \
            unzip \
            ca-certificates
        ./gradlew :launcher:linkReleaseExecutableLinuxX64
    '

OUT="$REPO_ROOT/launcher/build/bin/linuxX64/releaseExecutable/udeploy-launcher.kexe"
if [[ ! -f "$OUT" ]]; then
    echo "build finished but expected output not found at $OUT" >&2
    exit 1
fi

echo
echo "✓ Linux x64 binary built:"
echo "  $OUT"
echo
echo "Copy to your Ubuntu VM and run. Runtime requirements on the VM:"
echo "  sudo apt-get install -y libgtk-3-0 libwebkit2gtk-4.1-0"
