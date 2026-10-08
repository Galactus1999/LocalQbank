#!/usr/bin/env bash
set -euo pipefail

ROOT="$GITHUB_WORKSPACE"
SERIAL="emulator-$EMULATOR_PORT"
PROJECT="$ROOT/.adaptive-source"
LOG="$ROOT/.adaptive-instrumentation.log"
APK_DIR="$PROJECT/.ci-test-apks"
DEBUG_APK="$APK_DIR/app-debug.apk"
TEST_APK="$APK_DIR/app-debug-androidTest.apk"

: > "$LOG"
cleanup() {
  rc=$?
  if [[ "$rc" -ne 0 ]]; then
    {
      echo "===== adaptive instrumentation diagnostics ====="
      echo "exit=$rc serial=$SERIAL"
      adb -s "$SERIAL" shell pm list instrumentation 2>&1 || true
      adb -s "$SERIAL" shell pm list packages 2>&1 | head -300 || true
      adb -s "$SERIAL" logcat -d -v threadtime 2>&1 || true
    } >> "$LOG"
    cat "$LOG"
  fi
  exit "$rc"
}
trap cleanup EXIT

test -s "$DEBUG_APK"
test -s "$TEST_APK"
adb -s "$SERIAL" wait-for-device

boot_ok=false
for _ in $(seq 1 60); do
  if [[ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    boot_ok=true
    break
  fi
  sleep 2
done
[[ "$boot_ok" == true ]]

adb -s "$SERIAL" install -r -d "$DEBUG_APK"
adb -s "$SERIAL" install -r -d "$TEST_APK"

runner_line=""
for _ in $(seq 1 30); do
  runner_line="$(adb -s "$SERIAL" shell pm list instrumentation 2>/dev/null | tr -d '\r' | grep '^instrumentation:' | head -1 || true)"
  [[ -n "$runner_line" ]] && break
  sleep 1
done
[[ -n "$runner_line" ]]

runner="$(printf '%s\n' "$runner_line" | sed 's/^instrumentation://' | awk '{print $1}')"
[[ "$runner" == */* ]]
echo "Discovered instrumentation runner: $runner"

set +e
rm -f "$LOG"
adb -s "$SERIAL" shell am instrument -w -r -e no-isolated-storage 1 "$runner" > "$LOG" 2>&1 &
instrument_pid=$!
terminal_seen=false
for _ in $(seq 1 1800); do
  if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:|^INSTRUMENTATION_CODE: ' "$LOG"; then
    terminal_seen=true
    break
  fi
  if ! kill -0 "$instrument_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ "$terminal_seen" == true ]] && kill -0 "$instrument_pid" 2>/dev/null; then
  kill "$instrument_pid" 2>/dev/null || true
  wait "$instrument_pid" 2>/dev/null || true
  adb_rc=0
else
  wait "$instrument_pid"
  adb_rc=$?
fi
set -e
cat "$LOG"

if [[ "$adb_rc" -ne 0 ]]; then
  echo "ADB instrumentation invocation failed: rc=$adb_rc"
  exit "$adb_rc"
fi

if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:' "$LOG"; then
  echo "Instrumented tests reported runner failure or abort."
  exit 1
fi

if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -1$|^INSTRUMENTATION_STATUS_CODE: -2$' "$LOG"; then
  echo "At least one instrumented test reported a fatal status."
  exit 1
fi

status_ok_count="$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0$' "$LOG" || true)"
if [[ "$status_ok_count" -lt 1 ]]; then
  echo "No successful per-test instrumentation result was reported."
  exit 1
fi

if ! grep -Eq '^INSTRUMENTATION_CODE: -?[0-9]+$' "$LOG"; then
  echo "Instrumentation runner did not report a terminal result."
  exit 1
fi

VISUAL_DIR="$ROOT/.adaptive-visual"
mkdir -p "$VISUAL_DIR"
APP_ID="${ADAPTIVE_APPLICATION_ID:-com.localqbank.library}"
adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 3

capture_visual() {
  local name="$1"
  adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
  test -s "$VISUAL_DIR/$name.png"
  echo "Visual truth capture PASS: $VISUAL_DIR/$name.png"
}

tap_text() {
  local wanted="$1"
  local xml="$VISUAL_DIR/window-hierarchy.xml"
  adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1 || return 1
  adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$xml" 2>/dev/null || return 1
  python3 - "$xml" "$wanted" <<'PY'
import re,sys
xml,wanted=sys.argv[1],sys.argv[2]
s=open(xml,encoding="utf-8",errors="ignore").read()
pat=re.compile(r'<node[^>]*text="' + re.escape(wanted) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
m=pat.search(s)
if not m:
    sys.exit(1)
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
}

if adb -s "$SERIAL" shell pidof "$APP_ID" >/dev/null 2>&1; then
  capture_visual "home-light"
  # Capture the actual hierarchy alongside the pixels so geometry/text overflow
  # can be inspected without asking the user for screenshots.
  adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1 || true
  adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/home-window.xml" 2>/dev/null || true

  for target in "QBank" "Flashcards" "Ben"; do
    coords="$(tap_text "$target" 2>/dev/null || true)"
    if [[ "$coords" =~ ^[0-9]+[[:space:]][0-9]+$ ]]; then
      read -r tap_x tap_y <<< "$coords"
      adb -s "$SERIAL" shell input tap "$tap_x" "$tap_y" >/dev/null 2>&1 || true
      sleep 2
      safe="$(printf '%s' "$target" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')"
      capture_visual "$safe"
      adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
      adb -s "$SERIAL" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
      sleep 2
    else
      echo "Visual truth capture BLOCKED: text target '$target' not found"
    fi
  done

  # Pull the screenshots/hierarchies captured by the instrumentation test itself.
  # These are authoritative because the test launches the real production Activities.
  EXACT_DIR="$VISUAL_DIR/exact-production"
  DEVICE_EXACT_DIR="/sdcard/RovexVisualTruth"
  rm -rf "$EXACT_DIR"
  mkdir -p "$EXACT_DIR"
  if adb -s "$SERIAL" shell test -d "$DEVICE_EXACT_DIR"; then
    adb -s "$SERIAL" pull "$DEVICE_EXACT_DIR/." "$EXACT_DIR/" >/dev/null
    test -s "$EXACT_DIR/02_qbank.png"
    test -s "$EXACT_DIR/02_qbank-window.xml"
    test -s "$EXACT_DIR/04_ren.png"
    test -s "$EXACT_DIR/04_ren-window.xml"
    printf '02_qbank.png\\n02_qbank-window.xml\\n04_ren.png\\n04_ren-window.xml\\n' > "$EXACT_DIR/manifest.txt"
    echo "Exact production visual evidence PASS: instrumentation artifacts exported"
  else
    echo "Exact production visual evidence BLOCKED: instrumentation did not create /sdcard/RovexVisualTruth"
    exit 1
  fi

  # Landscape is an explicit visual contract, but failure to rotate an emulator
  # must not turn otherwise-valid functional instrumentation red.
  adb -s "$SERIAL" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell settings put system user_rotation 1 >/dev/null 2>&1 || true
  sleep 2
  capture_visual "home-landscape" || true
  adb -s "$SERIAL" shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
else
  echo "Visual truth capture BLOCKED: launcher did not start $APP_ID"
fi

if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

echo "Adaptive instrumentation suite PASS."
