#!/usr/bin/env bash
set -euo pipefail
ROOT="$GITHUB_WORKSPACE"
SERIAL="emulator-$EMULATOR_PORT"
PACKAGE="${ADAPTIVE_APPLICATION_ID:?ADAPTIVE_APPLICATION_ID is required}"
OUT="${1:?output directory required}"
mkdir -p "$OUT/screens"
log(){ printf '[visual-truth] %s\n' "$*"; }
fail(){ log "FATAL: $*"; exit 1; }
wait_stable(){ sleep "${1:-2}"; }

capture(){
  local label="$1" component="$2" extra="${3:-}"
  log "Launching $label: $PACKAGE/$component"
  adb -s "$SERIAL" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  if ! adb -s "$SERIAL" shell am start -W -n "$PACKAGE/$component" $extra >"$OUT/${label}.start.txt" 2>&1; then
    cat "$OUT/${label}.start.txt" >&2 || true
    fail "Unable to launch required visual screen: $label"
  fi
  wait_stable 2
  if ! adb -s "$SERIAL" shell dumpsys activity activities | grep -q "$component"; then
    cat "$OUT/${label}.start.txt" >&2 || true
    fail "Expected activity not foreground after launch: $label ($component)"
  fi
  adb -s "$SERIAL" exec-out screencap -p > "$OUT/screens/${label}.png"
  test -s "$OUT/screens/${label}.png"
  log "Captured $label"
}

capture "01_home" ".MainActivity"
capture "02_qbank" ".RovexSectionDashboardActivity" '--es section qbank'
capture "03_flashcards" ".RovexSectionDashboardActivity" '--es section flashcards'
capture "04_ren" ".RenActivity"
capture "05_settings" ".SettingsActivity"

if adb -s "$SERIAL" shell cmd package resolve-activity --brief "$PACKAGE/.VisualLabActivity" 2>/dev/null | grep -q 'VisualLabActivity'; then
  adb -s "$SERIAL" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  if adb -s "$SERIAL" shell am start -W -n "$PACKAGE/.VisualLabActivity" >"$OUT/06_visual_lab.start.txt" 2>&1; then
    wait_stable 2
    adb -s "$SERIAL" exec-out screencap -p > "$OUT/screens/06_visual_lab.png"
    log "Captured 06_visual_lab"
  else
    log "Visual Lab launch skipped after failed optional launch"
  fi
fi

adb -s "$SERIAL" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
python3 "$ROOT/tools/rovex_visual_truth.py" --screens "$OUT/screens" --output "$OUT/visual-truth.json"
cat "$OUT/visual-truth.json"
