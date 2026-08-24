#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE="$ROOT_DIR/.tools/gradle-8.9/bin/gradle"
[[ -x "$GRADLE" ]] || { echo "Run ./doit.sh once first." >&2; exit 1; }
"$GRADLE" -p "$ROOT_DIR" --no-daemon assembleRelease
echo "Unsigned release APK: $ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
