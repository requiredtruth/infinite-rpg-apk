#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-status}" in
    status) printf 'Infinite RPG source: ready\nLatest published demo: v0.5.3\n'; "$ROOT/test.sh" ;;
    test) shift; exec "$ROOT/test.sh" "$@" ;;
    build) shift; exec "$ROOT/android-build.sh" "$@" ;;
    release) shift; exec "$ROOT/build-release.sh" "$@" ;;
    *) echo "usage: ./cli.sh [status|test|build|release]" >&2; exit 2 ;;
esac
