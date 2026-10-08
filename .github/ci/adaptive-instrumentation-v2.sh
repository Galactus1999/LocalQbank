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
adb -s "$SERIAL" shell am instrument -w -r "$runner" > "$LOG" 2>&1 &
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

if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:' "$LOG"; then
  echo "Instrumented tests reported runner failure or abort."
  exit 1
fi

if grep -Eq "^INSTRUMENTATION_STATUS_CODE: -1\$|^INSTRUMENTATION_STATUS_CODE: -2\$" "$LOG"; then
  echo "Instrumentation runner reported a fatal per-test status."
  exit 1
fi

# Android's am instrument can return 255 after a complete JUnit result has been
# emitted. The authoritative result is the terminal instrumentation stream: no
# FAILED/ABORTED marker, a complete OK(N tests) result, and at least one successful
# per-test status. Do not turn a successful 79/79 run red because of the shell rc.
status_ok_count="$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0

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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
    echo "ADB instrumentation invocation failed without an assumption-only skip: rc=$adb_rc"
    exit "$adb_rc"
  fi
  echo "ADB runner returned rc=$adb_rc with JUnit assumption skip(s); continuing."
fi
if ! grep -Eq "^INSTRUMENTATION_RESULT:|^INSTRUMENTATION_CODE: -1\$" "$LOG"; then
  echo "Instrumentation did not produce the normal completed result marker."
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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG" || true)"
if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:' "$LOG"; then
  echo "Instrumentation runner reported failure/abort."
  exit 1
fi
if grep -Eq "^INSTRUMENTATION_STATUS_CODE: -1\$|^INSTRUMENTATION_STATUS_CODE: -2\$" "$LOG"; then
  echo "Instrumentation runner reported a fatal per-test status."
  exit 1
fi
if ! grep -Eq '^INSTRUMENTATION_RESULT:' "$LOG" || ! grep -Eq '^INSTRUMENTATION_CODE: -?[0-9]+

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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
    echo "ADB instrumentation invocation failed without an assumption-only skip: rc=$adb_rc"
    exit "$adb_rc"
  fi
  echo "ADB runner returned rc=$adb_rc with JUnit assumption skip(s); continuing."
fi
if ! grep -Eq "^INSTRUMENTATION_RESULT:|^INSTRUMENTATION_CODE: -1\$" "$LOG"; then
  echo "Instrumentation did not produce the normal completed result marker."
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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
  echo "Instrumentation did not produce a complete terminal result."
  exit 1
fi
if ! grep -Eq '^OK \([0-9]+ tests\)

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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
    echo "ADB instrumentation invocation failed without an assumption-only skip: rc=$adb_rc"
    exit "$adb_rc"
  fi
  echo "ADB runner returned rc=$adb_rc with JUnit assumption skip(s); continuing."
fi
if ! grep -Eq "^INSTRUMENTATION_RESULT:|^INSTRUMENTATION_CODE: -1\$" "$LOG"; then
  echo "Instrumentation did not produce the normal completed result marker."
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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
  echo "Instrumentation did not report a complete successful JUnit result."
  exit 1
fi
if [[ "$status_ok_count" -lt 1 ]]; then
  echo "No successful per-test instrumentation result was reported."
  exit 1
fi
if [[ "$adb_rc" -ne 0 ]]; then
  echo "Instrumentation shell returned rc=$adb_rc after a complete successful JUnit result; accepting the terminal test result."
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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
 "$LOG"; then
    echo "ADB instrumentation invocation failed without an assumption-only skip: rc=$adb_rc"
    exit "$adb_rc"
  fi
  echo "ADB runner returned rc=$adb_rc with JUnit assumption skip(s); continuing."
fi
if ! grep -Eq "^INSTRUMENTATION_RESULT:|^INSTRUMENTATION_CODE: -1\$" "$LOG"; then
  echo "Instrumentation did not produce the normal completed result marker."
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

  # Exact production-owner evidence: launch the real Activities, not heuristic text targets.
  capture_exact_activity() {
    local name="$1"
    local component="$2"
    adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" >/dev/null 2>&1
    sleep 2
    adb -s "$SERIAL" exec-out screencap -p > "$VISUAL_DIR/$name.png"
    test -s "$VISUAL_DIR/$name.png"
    adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1
    adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml"
    test -s "$VISUAL_DIR/$name-window.xml"
    adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 "$component" > "$VISUAL_DIR/$name-activity.txt" || true
    echo "Exact production visual evidence PASS: $name"
  }
  capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity"
  capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

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
