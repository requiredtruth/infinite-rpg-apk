#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

test ! -d .signing
if find . -path './.tools' -prune -o -type f \( -iname '*.jks' -o -iname '*.keystore' -o -iname '*.gguf' \) -print -quit | grep -q .; then
  echo "Private signing material or model weights must not be committed." >&2
  exit 1
fi

private_namespace='systems[./_]'"'ig'"'neos'
private_projects='world'"'forge'"'|ever'"'forge'"
if grep -RIE "${private_namespace}|${private_projects}" \
  --exclude='*.png' --exclude='*.jpg' --exclude='*.jpeg' --exclude-dir='.git' --exclude-dir='.tools' .; then
  echo "Private namespace or unrelated project reference found." >&2
  exit 1
fi

grep -q "namespace 'app.infiniterpg'" app/build.gradle
grep -q "applicationId 'app.infiniterpg'" app/build.gradle
grep -q 'android:name="app.infiniterpg.InfiniteRpgApp"' app/src/main/AndroidManifest.xml
grep -q 'System.loadLibrary("infinite_rpg_llama")' app/src/main/java/app/infiniterpg/ai/NativeLlama.java
grep -q 'Java_app_infiniterpg_ai_NativeLlama_load' app/src/main/cpp/native_llama.cpp

test "$(find app/src/main/java/app/infiniterpg -name '*.java' | wc -l)" -ge 20
for asset in app/src/main/res/drawable-nodpi/*.png; do test -s "$asset"; done

bash -n doit.sh
bash -n build-release.sh

echo "Infinite RPG source contract verified."
