#!/usr/bin/env bash
set -euo pipefail
ROOT="$GITHUB_WORKSPACE"
SERIAL="emulator-$EMULATOR_PORT"
OUT="${1:?output directory required}"
mkdir -p "$OUT/screens"
log(){ printf '[visual-truth] %s\n' "$*"; }
fail(){ log "FATAL: $*"; exit 1; }

# Activities are intentionally non-exported in Rovex. The companion instrumented
# test launches them inside the application UID, then leaves real screenshots
# on the emulator for this host-side collector.
adb -s "$SERIAL" shell test -d /sdcard/RovexVisualTruth || fail "Instrumented visual truth directory missing"
rm -rf "$OUT/screens"
mkdir -p "$OUT/screens"
adb -s "$SERIAL" pull /sdcard/RovexVisualTruth/. "$OUT/screens/" >/dev/null
for required in 01_home.png 02_qbank.png 03_flashcards.png 04_ren.png 05_settings.png; do
  test -s "$OUT/screens/$required" || fail "Required rendered screenshot missing: $required"
done
count="$(find "$OUT/screens" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
log "Collected $count actual rendered screenshots"

python3 "$ROOT/tools/rovex_visual_truth.py" --screens "$OUT/screens" --output "$OUT/visual-truth.json"
cat "$OUT/visual-truth.json"
