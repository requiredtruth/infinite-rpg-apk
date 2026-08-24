#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$ROOT_DIR/.tools"
SDK_DIR="$TOOLS_DIR/android-sdk"
GRADLE_VERSION="8.9"
LLAMA_COMMIT="9731ad3f29da96f588711a0d1eb08cf210721e16"

mkdir -p "$TOOLS_DIR" "$SDK_DIR/cmdline-tools"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }; }
need java
need curl
need unzip
need git

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Downloading Android command-line tools..."
  archive="$TOOLS_DIR/android-commandlinetools.zip"
  curl -fL --retry 3 -o "$archive" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$SDK_DIR/cmdline-tools/latest" "$TOOLS_DIR/cmdline-unpack"
  mkdir -p "$TOOLS_DIR/cmdline-unpack"
  unzip -q "$archive" -d "$TOOLS_DIR/cmdline-unpack"
  mv "$TOOLS_DIR/cmdline-unpack/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export GRADLE_USER_HOME="$TOOLS_DIR/gradle-home"
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "cmake;3.22.1"

LLAMA_DIR="$ROOT_DIR/app/src/main/cpp/third_party/llama.cpp"
if [[ ! -d "$LLAMA_DIR/.git" ]]; then
  echo "Downloading pinned llama.cpp $LLAMA_COMMIT..."
  mkdir -p "$(dirname "$LLAMA_DIR")"
  git init "$LLAMA_DIR"
  git -C "$LLAMA_DIR" remote add origin https://github.com/ggerganov/llama.cpp.git
  git -C "$LLAMA_DIR" fetch --depth 1 origin "$LLAMA_COMMIT"
  git -C "$LLAMA_DIR" checkout --detach FETCH_HEAD
fi

if [[ ! -x "$TOOLS_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -fL --retry 3 -o "$TOOLS_DIR/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q -o "$TOOLS_DIR/gradle.zip" -d "$TOOLS_DIR"
fi

echo "sdk.dir=$SDK_DIR" > "$ROOT_DIR/local.properties"
"$TOOLS_DIR/gradle-$GRADLE_VERSION/bin/gradle" -p "$ROOT_DIR" --no-daemon clean assembleDebug
cp "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" "$ROOT_DIR/Infinite-RPG-debug.apk"
echo
echo "APK ready: $ROOT_DIR/Infinite-RPG-debug.apk"
