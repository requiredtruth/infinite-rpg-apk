#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for command_name in python3 java curl unzip git; do
    command -v "$command_name" >/dev/null 2>&1 || { echo "Missing required command: $command_name" >&2; exit 1; }
done
VENV="$ROOT/.venv"
[[ -x "$VENV/bin/python" ]] || python3 -m venv "$VENV"
"$VENV/bin/python" -m pip install --disable-pip-version-check --upgrade pip PySide6
touch "$VENV/.repo-gui-ready"
echo "Infinite RPG desktop build manager is ready."
echo "Use ./run.sh, then choose Build APK; or use ./cli.sh build."
