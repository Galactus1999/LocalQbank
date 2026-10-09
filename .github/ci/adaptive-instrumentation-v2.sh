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
      adb -s "$SERIAL" shell logcat -d -v threadtime 2>&1 || true
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
  if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:|^INSTRUMENTATION_CODE: -?[0-9]+$' "$LOG"; then
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

# The instrumentation run can complete successfully while the host adb daemon
# transiently dies during the transition to visual/interaction capture. Recover
# the transport before treating any post-test adb operation as an application
# failure.
recover_adb() {
  for _ in $(seq 1 8); do
    adb start-server >/dev/null 2>&1 || true
    state="$(adb -s "$SERIAL" get-state 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" == "device" ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}
if ! recover_adb; then
  echo "ADB transport could not be recovered after a completed instrumentation run."
  exit 1
fi

if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:' "$LOG"; then
  echo "Instrumented tests reported runner failure or abort."
  exit 1
fi

if grep -Eq '^INSTRUMENTATION_RESULT: shortMsg=Process crashed\.
if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -1$|^INSTRUMENTATION_STATUS_CODE: -2$' "$LOG"; then
  echo "Instrumentation runner reported a fatal per-test status."
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

# am instrument may return a non-zero wrapper status after emitting a complete,
# successful JUnit result. Terminal instrumentation markers + per-test statuses
# are authoritative; a wrapper rc alone is not an application failure.
if [[ "$adb_rc" -ne 0 ]]; then
  echo "Instrumentation wrapper rc=$adb_rc after terminal result; continuing because the JUnit result is complete."
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
  adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1 || true
  adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml" 2>/dev/null || true
  echo "Visual truth capture PASS: $name"
}

capture_exact_activity() {
  local name="$1"
  local component="$2"
  shift 2
  adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" "$@" >/dev/null 2>&1 || true
  sleep 3
  local resumed
  resumed="$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -m1 "$component" || true)"
  printf '%s resumed=%s\n' "$name" "$resumed" > "$VISUAL_DIR/$name-activity.txt"
  capture_visual "$name"
}

capture_visual "home-production"
capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity" --es section qbank
capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"
capture_exact_activity "flashcards-production" "com.localqbank.library.RovexSectionDashboardActivity" --es section flashcards
capture_exact_activity "settings-production" "com.localqbank.library.SettingsActivity"
capture_exact_activity "visual-lab-production" "com.localqbank.library.VisualLabActivity"

# Interaction exploration is separate from the JUnit result. A control that
# intentionally closes an Activity is classified as expected navigation by the
# explorer rather than as an app crash.
if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

# Execute the checked-in Macrobenchmark module rather than treating app-module
# smoke instrumentation as performance evidence. Keep the run on this same emulator
# and preserve raw AndroidX benchmark output for review before any optimization.
if [[ -f "$PROJECT/benchmark/build.gradle.kts" ]]; then
  echo "===== reproducible Macrobenchmark baseline ====="
  (
    cd "$PROJECT"
    bash ./gradlew :benchmark:connectedAndroidTest --no-daemon --stacktrace
  )
  BENCHMARK_OUTPUT="$PROJECT/benchmark/build/outputs"
  if [[ -d "$BENCHMARK_OUTPUT" ]]; then
    mkdir -p "$VISUAL_DIR/macrobenchmark"
    find "$BENCHMARK_OUTPUT" -type f \( -path '*connected_android_test_additional_output*' -o -path '*connectedAndroidTest*' \) -print
    while IFS= read -r report; do
      [[ -f "$report" ]] || continue
      rel="${report#"$BENCHMARK_OUTPUT"/}"
      mkdir -p "$VISUAL_DIR/macrobenchmark/$(dirname "$rel")"
      cp -f "$report" "$VISUAL_DIR/macrobenchmark/$rel"
    done < <(find "$BENCHMARK_OUTPUT" -type f \( -path '*connected_android_test_additional_output*' -o -path '*connectedAndroidTest*' \))
  fi
else
  echo "Macrobenchmark baseline BLOCKED: benchmark/build.gradle.kts missing from selected source."
  exit 1
fi

echo "Adaptive instrumentation and Macrobenchmark baseline PASS."
 "$LOG"; then
  echo "Instrumented test process crashed before the suite completed; refusing to treat a partial run as PASS."
  exit 1
fi

if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -1$|^INSTRUMENTATION_STATUS_CODE: -2$' "$LOG"; then
  echo "Instrumentation runner reported a fatal per-test status."
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

# am instrument may return a non-zero wrapper status after emitting a complete,
# successful JUnit result. Terminal instrumentation markers + per-test statuses
# are authoritative; a wrapper rc alone is not an application failure.
if [[ "$adb_rc" -ne 0 ]]; then
  echo "Instrumentation wrapper rc=$adb_rc after terminal result; continuing because the JUnit result is complete."
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
  adb -s "$SERIAL" shell uiautomator dump /sdcard/rovex-window.xml >/dev/null 2>&1 || true
  adb -s "$SERIAL" exec-out cat /sdcard/rovex-window.xml > "$VISUAL_DIR/$name-window.xml" 2>/dev/null || true
  echo "Visual truth capture PASS: $name"
}

capture_exact_activity() {
  local name="$1"
  local component="$2"
  shift 2
  adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell am start -W -n "$APP_ID/$component" "$@" >/dev/null 2>&1 || true
  sleep 3
  local resumed
  resumed="$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -m1 "$component" || true)"
  printf '%s resumed=%s\n' "$name" "$resumed" > "$VISUAL_DIR/$name-activity.txt"
  capture_visual "$name"
}

capture_visual "home-production"
capture_exact_activity "qbank-production" "com.localqbank.library.RovexSectionDashboardActivity" --es section qbank
capture_exact_activity "ren-production" "com.localqbank.library.RenActivity"

# Interaction exploration is separate from the JUnit result. A control that
# intentionally closes an Activity is classified as expected navigation by the
# explorer rather than as an app crash.
if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

# Execute the checked-in Macrobenchmark module rather than treating app-module
# smoke instrumentation as performance evidence. Keep the run on this same emulator
# and preserve raw AndroidX benchmark output for review before any optimization.
if [[ -f "$PROJECT/benchmark/build.gradle.kts" ]]; then
  echo "===== reproducible Macrobenchmark baseline ====="
  (
    cd "$PROJECT"
    ./gradlew :benchmark:connectedAndroidTest --no-daemon --stacktrace
  )
  BENCHMARK_OUTPUT="$PROJECT/benchmark/build/outputs"
  if [[ -d "$BENCHMARK_OUTPUT" ]]; then
    mkdir -p "$VISUAL_DIR/macrobenchmark"
    find "$BENCHMARK_OUTPUT" -type f \( -path '*connected_android_test_additional_output*' -o -path '*connectedAndroidTest*' \) -print
    while IFS= read -r report; do
      [[ -f "$report" ]] || continue
      rel="${report#"$BENCHMARK_OUTPUT"/}"
      mkdir -p "$VISUAL_DIR/macrobenchmark/$(dirname "$rel")"
      cp -f "$report" "$VISUAL_DIR/macrobenchmark/$rel"
    done < <(find "$BENCHMARK_OUTPUT" -type f \( -path '*connected_android_test_additional_output*' -o -path '*connectedAndroidTest*' \))
  fi
else
  echo "Macrobenchmark baseline BLOCKED: benchmark/build.gradle.kts missing from selected source."
  exit 1
fi

echo "Adaptive instrumentation and Macrobenchmark baseline PASS."
