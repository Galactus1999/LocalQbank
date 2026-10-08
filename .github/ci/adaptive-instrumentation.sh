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
timeout 1800s adb -s "$SERIAL" shell am instrument -w -r "$runner" > "$LOG" 2>&1
adb_rc=$?
set -e
cat "$LOG"

if [[ "$adb_rc" -ne 0 ]]; then
  echo "ADB invocation itself failed: rc=$adb_rc"
  exit "$adb_rc"
fi

if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:|^INSTRUMENTATION_STATUS_CODE: -1$|^INSTRUMENTATION_STATUS_CODE: -2$' "$LOG"; then
  echo "Instrumented tests reported an error, assertion failure, or runner abort."
  exit 1
fi

if ! grep -Eq '^INSTRUMENTATION_STATUS_CODE: 0$' "$LOG"; then
  echo "No successful per-test instrumentation result was reported."
  exit 1
fi

if [[ "$ADAPTIVE_IS_ROVEX" == "true" ]]; then
  visual_out="$RUNNER_TEMP/adaptive-android/reports/visual-truth"
  bash "$GITHUB_WORKSPACE/.github/ci/rovex_visual_truth_capture.sh" "$visual_out"
  test -s "$visual_out/visual-truth.json"
  echo "Rendered visual truth gate PASS."
fi

echo "Adaptive instrumentation suite PASS."
