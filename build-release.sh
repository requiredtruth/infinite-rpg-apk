#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
GRADLE="$ROOT_DIR/.tools/gradle-8.9/bin/gradle"
[[ -x "$GRADLE" ]] || "$ROOT_DIR/android-build.sh"
"$GRADLE" -p "$ROOT_DIR" --no-daemon assembleRelease
echo "Unsigned release APK: $ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
