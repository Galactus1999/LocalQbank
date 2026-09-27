#!/bin/sh
set -eu
SERIAL="emulator-${EMULATOR_PORT:-5554}"
WORKSPACE="$PWD"
DEBUG_APK="$WORKSPACE/.ci-test-apks/app-debug.apk"
TEST_APK="$WORKSPACE/.ci-test-apks/app-debug-androidTest.apk"
INSTRUMENTATION_LOG="$WORKSPACE/.ci-instrumentation.log"
echo "Direct instrumentation workspace: $WORKSPACE"
echo "Target emulator serial: $SERIAL"
test -f "$WORKSPACE/gradlew"
test -s "$DEBUG_APK"
test -s "$TEST_APK"
: > "$INSTRUMENTATION_LOG"
dump_diagnostics() {
  rc=$?
  if [ "$rc" -ne 0 ]; then
    {
      echo
      echo "===== CI DIRECT INSTRUMENTATION FAILURE DIAGNOSTICS ====="
      echo "exit_code=$rc"
      echo "serial=$SERIAL"
      echo "===== pm list instrumentation ====="
      adb -s "$SERIAL" shell pm list instrumentation 2>&1 || true
      echo "===== pm list packages ====="
      adb -s "$SERIAL" shell pm list packages 2>&1 | grep -F 'com.localqbank.library' || true
      echo "===== logcat ====="
      adb -s "$SERIAL" logcat -d -v threadtime 2>&1 || true
    } >> "$INSTRUMENTATION_LOG"
    cat "$INSTRUMENTATION_LOG"
  fi
  exit "$rc"
}
trap dump_diagnostics EXIT
adb -s "$SERIAL" wait-for-device
boot_attempt=1
boot_completed=""
while [ "$boot_attempt" -le 30 ]; do
  boot_completed="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "$boot_completed" = "1" ]; then break; fi
  sleep 1
  boot_attempt=$((boot_attempt + 1))
done
test "$boot_completed" = "1"
adb -s "$SERIAL" install -r -d "$DEBUG_APK"
adb -s "$SERIAL" install -r -d "$TEST_APK"
instrumentation=""
attempt=1
while [ "$attempt" -le 30 ]; do
  instrumentation="$(adb -s "$SERIAL" shell pm list instrumentation 2>/dev/null | tr -d '\r' | grep -E '^instrumentation:.*target=com\.localqbank\.library([[:space:]]|$)' | head -1 || true)"
  if [ -n "$instrumentation" ]; then break; fi
  sleep 1
  attempt=$((attempt + 1))
done
if [ -z "$instrumentation" ]; then
  echo "No instrumentation targeting com.localqbank.library was registered after 30 seconds."
  adb -s "$SERIAL" shell pm list instrumentation || true
  adb -s "$SERIAL" shell pm list packages | grep -F 'com.localqbank.library' || true
  exit 1
fi
runner_spec="${instrumentation#instrumentation:}"
runner_spec="${runner_spec%% *}"
test -n "$runner_spec"
case "$runner_spec" in
  com.localqbank.library.test/*) ;;
  *) echo "Unexpected instrumentation runner/package: $runner_spec"; exit 1 ;;
esac
echo "Registered instrumentation: $runner_spec"
set +e
timeout 1200s adb -s "$SERIAL" shell am instrument -w -r \
  -e class 'com.localqbank.library.QBankDbSearchRegressionTest,com.localqbank.library.QBankDeletionIsolationTest,com.localqbank.library.QBankImportIdentityRegressionTest' \
  "$runner_spec" > "$INSTRUMENTATION_LOG" 2>&1
instrumentation_rc=$?
set -e
cat "$INSTRUMENTATION_LOG"
case "$instrumentation_rc" in
  0) ;;
  124|137) echo "Direct instrumentation timed out (rc=$instrumentation_rc)."; exit "$instrumentation_rc" ;;
  *) echo "adb/timeout invocation failed (rc=$instrumentation_rc)."; exit "$instrumentation_rc" ;;
esac
if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -1$|^INSTRUMENTATION_STATUS_CODE: -2$' "$INSTRUMENTATION_LOG"; then
  echo "Instrumented regression suite reported a test error/failure."
  exit 1
fi
for expected_class in \
  com.localqbank.library.QBankDbSearchRegressionTest \
  com.localqbank.library.QBankDeletionIsolationTest \
  com.localqbank.library.QBankImportIdentityRegressionTest; do
  grep -Fq "INSTRUMENTATION_STATUS: class=$expected_class" "$INSTRUMENTATION_LOG" || {
    echo "Expected regression class did not execute: $expected_class"
    exit 1
  }
done
success_count="$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0$' "$INSTRUMENTATION_LOG" || true)"
test "$success_count" -gt 0
echo "Direct AndroidJUnitRunner regression suite PASS: $success_count successful test-result frame(s), zero -1/-2 failure frames."
