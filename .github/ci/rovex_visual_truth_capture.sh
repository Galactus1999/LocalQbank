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

# Collect Home screenshots captured by the Phase 1 instrumented theme regression test.
# These are real UIAutomation PNGs after ThemeManager switches the live Activity theme.
PHASE1_REMOTE_DIR="/sdcard/Android/data/${ADAPTIVE_APPLICATION_ID:-com.localqbank.library}/files"
for theme in light amoled mint sunset lavender pastel; do
  remote="$PHASE1_REMOTE_DIR/phase1_home_${theme}.png"
  local="$OUT/screens/phase1_home_${theme}.png"
  if ! adb -s "$SERIAL" shell test -s "$remote"; then
    found="$(adb -s "$SERIAL" shell "find \"$PHASE1_REMOTE_DIR\" -maxdepth 5 -type f -name \"phase1_home_${theme}.png\" -print 2>/dev/null | head -1" | tr -d "\r")"
    if [[ -n "$found" ]] && adb -s "$SERIAL" shell test -s "$found"; then
      remote="$found"
      log "Resolved theme=$theme screenshot at $remote"
    else
      log "Phase 1 capture missing; app-specific screenshot files:"
      adb -s "$SERIAL" shell "find \"$PHASE1_REMOTE_DIR\" -maxdepth 5 -type f -name \"phase1_home_*.png\" -print 2>/dev/null" || true
      adb -s "$SERIAL" logcat -d -s RovexVisualTruth:I "*:S" 2>/dev/null || true
      fail "Phase 1 rendered Home screenshot missing for theme=$theme under $PHASE1_REMOTE_DIR"
    fi
  fi
  adb -s "$SERIAL" pull "$remote" "$local" >/dev/null
  test -s "$local" || fail "Phase 1 rendered Home screenshot could not be collected for theme=$theme"
  log "Captured Phase 1 theme=$theme bytes=$(stat -c%s "$local") sha256=$(sha256sum "$local" | awk '{print $1}')"
done

count="$(find "$OUT/screens" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
log "Collected $count actual rendered screenshots including Phase 1 theme variants"

python3 "$ROOT/tools/rovex_visual_truth.py" --screens "$OUT/screens" --output "$OUT/visual-truth.json"
cat "$OUT/visual-truth.json"
