#!/usr/bin/env bash
# Packages the Magisk module zip: magisk/ tree + staged APK.
# Usage: tools/make-module.sh [path-to-apk]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
VERSION="$(grep -E '^version=' "$ROOT/magisk/module.prop" | cut -d= -f2 | tr -d '[:space:]')"
OUT_DIR="$ROOT/dist"
OUT="$OUT_DIR/magisk-browser-${VERSION}.zip"

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; echo "Build first: ./gradlew :app:assembleDebug" >&2; exit 1; }

mkdir -p "$OUT_DIR"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

cp -r "$ROOT/magisk/." "$STAGE/"
mkdir -p "$STAGE/apk"
cp "$APK" "$STAGE/apk/WolfyBrowser.apk"

rm -f "$OUT"
if command -v zip >/dev/null 2>&1; then
  ( cd "$STAGE" && zip -qr "$OUT" . )
else
  python3 - "$STAGE" "$OUT" <<'PY'
import os, sys, zipfile
stage, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _, files in os.walk(stage):
        for f in files:
            p = os.path.join(base, f)
            z.write(p, os.path.relpath(p, stage))
PY
fi

echo "Module zip: $OUT"
unzip -l "$OUT" 2>/dev/null | tail -n +2 | head -20 || true
