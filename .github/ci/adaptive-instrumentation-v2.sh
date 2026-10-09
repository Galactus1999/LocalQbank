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

if grep -Eq '^INSTRUMENTATION_RESULT: shortMsg=Process crashed\.' "$LOG"; then
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

# Non-exported Activities cannot be launched reliably with host-side am start.
# Run the dedicated instrumentation test so AndroidX launches each real Activity
# under the test UID, then validate the actual rendered screenshots and uniqueness.
adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
VISUAL_TEST_LOG="$VISUAL_DIR/visual-truth-instrumentation.log"
set +e
adb -s "$SERIAL" shell am instrument -w -r \
  -e class com.localqbank.library.RovexVisualTruthCaptureTest \
  -e rovexVisualTruth true "$runner" > "$VISUAL_TEST_LOG" 2>&1
visual_rc=$?
set -e
cat "$VISUAL_TEST_LOG"
if grep -Eq '^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_ABORTED:|FAILURES!!!|shortMsg=Process crashed' "$VISUAL_TEST_LOG" ||
   ! grep -Fq 'INSTRUMENTATION_CODE: -1' "$VISUAL_TEST_LOG" ||
   ! grep -Fq 'INSTRUMENTATION_STATUS_CODE: 0' "$VISUAL_TEST_LOG" ||
   ! grep -q 'test=captureCoreRenderedScreens' "$VISUAL_TEST_LOG"; then
  echo "Rendered visual truth instrumentation failed; refusing to publish host/launcher screenshots as app UI."
  exit 1
fi
if [[ "$visual_rc" -ne 0 ]]; then
  echo "Visual instrumentation wrapper rc=$visual_rc after complete successful JUnit terminal markers; continuing with verified screenshot collection."
fi
bash "$ROOT/.github/ci/rovex_visual_truth_capture.sh" "$VISUAL_DIR"

# Interaction exploration must observe real visible nodes. Empty hierarchy or zero
# explored actions is a failure, not a successful exhaustive test.
if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

# Macrobenchmark is intentionally NOT run on this emulator lane.
# AndroidX Benchmark rejects emulator execution and a debuggable target. Keep the
# benchmark source/tests intact, and report the capability honestly rather than
# turning an unsupported benchmark environment into a functional-test failure.
BENCHMARK_STATUS="$VISUAL_DIR/macrobenchmark-status.txt"
mkdir -p "$VISUAL_DIR"
if [[ -f "$PROJECT/benchmark/build.gradle.kts" ]]; then
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=This CI lane uses an Android emulator and a debuggable app target; AndroidX Macrobenchmark requires a supported non-debuggable target/device."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=true"
  } | tee "$BENCHMARK_STATUS"
else
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=Selected source archive does not contain benchmark/build.gradle.kts; no benchmark suite was executed."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=unknown"
  } | tee "$BENCHMARK_STATUS"
fi

echo "Functional instrumentation and interaction exploration PASS; Macrobenchmark lane BLOCKED / NOT RUN."
 "$VISUAL_TEST_LOG" ||
   ! grep -Eq '^INSTRUMENTATION_STATUS_CODE: 0
bash "$ROOT/.github/ci/rovex_visual_truth_capture.sh" "$VISUAL_DIR"

# Interaction exploration must observe real visible nodes. Empty hierarchy or zero
# explored actions is a failure, not a successful exhaustive test.
if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

# Macrobenchmark is intentionally NOT run on this emulator lane.
# AndroidX Benchmark rejects emulator execution and a debuggable target. Keep the
# benchmark source/tests intact, and report the capability honestly rather than
# turning an unsupported benchmark environment into a functional-test failure.
BENCHMARK_STATUS="$VISUAL_DIR/macrobenchmark-status.txt"
mkdir -p "$VISUAL_DIR"
if [[ -f "$PROJECT/benchmark/build.gradle.kts" ]]; then
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=This CI lane uses an Android emulator and a debuggable app target; AndroidX Macrobenchmark requires a supported non-debuggable target/device."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=true"
  } | tee "$BENCHMARK_STATUS"
else
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=Selected source archive does not contain benchmark/build.gradle.kts; no benchmark suite was executed."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=unknown"
  } | tee "$BENCHMARK_STATUS"
fi

echo "Functional instrumentation and interaction exploration PASS; Macrobenchmark lane BLOCKED / NOT RUN."
 "$VISUAL_TEST_LOG" ||
   ! grep -q 'test=captureCoreRenderedScreens' "$VISUAL_TEST_LOG"; then
  echo "Rendered visual truth instrumentation failed; refusing to publish host/launcher screenshots as app UI."
  exit 1
fi
if [[ "$visual_rc" -ne 0 ]]; then
  echo "Visual instrumentation wrapper rc=$visual_rc after complete successful JUnit terminal markers; continuing with verified screenshot collection."
fi
bash "$ROOT/.github/ci/rovex_visual_truth_capture.sh" "$VISUAL_DIR"

# Interaction exploration must observe real visible nodes. Empty hierarchy or zero
# explored actions is a failure, not a successful exhaustive test.
if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

# Macrobenchmark is intentionally NOT run on this emulator lane.
# AndroidX Benchmark rejects emulator execution and a debuggable target. Keep the
# benchmark source/tests intact, and report the capability honestly rather than
# turning an unsupported benchmark environment into a functional-test failure.
BENCHMARK_STATUS="$VISUAL_DIR/macrobenchmark-status.txt"
mkdir -p "$VISUAL_DIR"
if [[ -f "$PROJECT/benchmark/build.gradle.kts" ]]; then
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=This CI lane uses an Android emulator and a debuggable app target; AndroidX Macrobenchmark requires a supported non-debuggable target/device."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=true"
  } | tee "$BENCHMARK_STATUS"
else
  {
    echo "status=BLOCKED / NOT RUN"
    echo "reason=Selected source archive does not contain benchmark/build.gradle.kts; no benchmark suite was executed."
    echo "suite=benchmark:connectedAndroidTest"
    echo "tests_preserved=unknown"
  } | tee "$BENCHMARK_STATUS"
fi

echo "Functional instrumentation and interaction exploration PASS; Macrobenchmark lane BLOCKED / NOT RUN."
