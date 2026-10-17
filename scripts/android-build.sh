#!/usr/bin/env bash
# Build a Lart Android APK on Windows without Developer Mode.
# tauri-cli symlinks the Rust library into jniLibs, which Windows refuses
# without Developer Mode; this copies it and runs Gradle itself.
# usage: scripts/android-build.sh [aarch64|x86_64] [debug|release]
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/android-env.sh
arch="${1:-aarch64}"; profile="${2:-debug}"
case "$arch" in
  aarch64) abi=arm64-v8a; flavor=Arm64 ;;
  x86_64) abi=x86_64; flavor=X86_64 ;;
  *) echo "unknown arch $arch" >&2; exit 2 ;;
esac
Profile="$(tr '[:lower:]' '[:upper:]' <<<"${profile:0:1}")${profile:1}"
flag=""; [ "$profile" = debug ] && flag="--debug"
# Compiles the frontend and the Rust library; the final symlink step fails on
# Windows without Developer Mode, which is expected and handled below.
stamp="$(mktemp)"
pnpm tauri android build --apk $flag --target "$arch" || true
so="src-tauri/target/$arch-linux-android/$profile/liblart_lib.so"
[ "$so" -nt "$stamp" ] || { echo "Rust build failed: $so was not rebuilt" >&2; exit 1; }
rm -f "$stamp"
mkdir -p "src-tauri/gen/android/app/src/main/jniLibs/$abi"
dest="src-tauri/gen/android/app/src/main/jniLibs/$abi/liblart_lib.so"
cp "$so" "$dest"
# Release: drop symbol tables (about 14 MB on arm64).
[ "$profile" = release ] && "$NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe" --strip-unneeded "$dest"
cd src-tauri/gen/android
./gradlew.bat "assemble$flavor$Profile" -x "rustBuild$flavor$Profile" --console=plain
find app/build/outputs/apk -name "*.apk" -newer "../../../$so" -print
