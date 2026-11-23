#!/usr/bin/env bash
set -euo pipefail

: "\${ADAPTIVE_RELEASE_SMOKE_APK:?ADAPTIVE_RELEASE_SMOKE_APK is required}"
: "\${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is required}"

launch_log="\${RUNNER_TEMP:-/tmp}/rovex-release-launch.txt"
instrumentation_log="\${RUNNER_TEMP:-/tmp}/rovex-r8-runtime-tests.txt"
logcat_file="$GITHUB_WORKSPACE/release-smoke-logcat.txt"
activities_file="$GITHUB_WORKSPACE/release-smoke-activities.txt"
exit_info_file="$GITHUB_WORKSPACE/release-smoke-exit-info.txt"

adb install -r "$ADAPTIVE_RELEASE_SMOKE_APK"
adb logcat -c

# am start -W can time out while Android is still bringing up the first
# activity. Preserve its output, then independently verify process readiness.
if ! adb shell am start -W -n com.localqbank.library/.RovexSplashActivity >"$launch_log" 2>&1; then
  echo "WARN: am start -W returned non-zero; checking bounded process readiness."
  cat "$launch_log" || true
fi

deadline=$((SECONDS + 30))
pid=""
while (( SECONDS < deadline )); do
  pid="$(adb shell pidof com.localqbank.library 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$pid" ]]; then
    break
  fi
  sleep 1
done

# Capture diagnostics before any failure assertion, including activity and
# process-exit history. This runs even when startup never produces a PID.
adb logcat -d -v threadtime >"$logcat_file" 2>&1 || true
adb shell dumpsys activity activities >"$activities_file" 2>&1 || true
adb shell dumpsys activity exit-info com.localqbank.library >"$exit_info_file" 2>&1 || true

if [[ -z "$pid" ]]; then
  echo "FAIL: release APK process did not become ready within 30 seconds."
  grep -E 'FATAL EXCEPTION|Fatal signal|AndroidRuntime|has died|Force finishing activity|am_crash|am_anr|ANR in' "$logcat_file" | tail -n 160 || true
  cat "$activities_file" || true
  cat "$exit_info_file" || true
  exit 1
fi

if grep -E 'FATAL EXCEPTION|Fatal signal|am_crash|am_anr|ANR in' "$logcat_file"; then
  echo "FAIL: fatal crash or ANR marker detected during release APK startup."
  exit 1
fi

if [[ "\${ADAPTIVE_HAS_TEST_APK:-false}" == "true" && -s "\${ADAPTIVE_RELEASE_TEST_APK:-}" ]]; then
  adb install -r "$ADAPTIVE_RELEASE_TEST_APK"
  if ! adb shell am instrument -w \
    -e class com.localqbank.library.HtmlImportSecurityInstrumentedTest,com.localqbank.library.ZstdNativePayloadInstrumentedTest \
    com.localqbank.library.test/androidx.test.runner.AndroidJUnitRunner >"$instrumentation_log" 2>&1; then
    cat "$instrumentation_log" || true
    echo "FAIL: R8 release WebView/JNI instrumentation command failed."
    exit 1
  fi
  cat "$instrumentation_log"
  if ! grep -Eq 'OK \([0-9]+ tests?\)' "$instrumentation_log"; then
    echo "FAIL: R8 release WebView/JNI instrumentation did not report a passing test summary."
    exit 1
  fi
else
  echo "FAIL: no instrumentation APK available for R8 release WebView/JNI runtime checks."
  exit 1
fi

# Include instrumentation-time failures in the uploaded diagnostics as well.
adb logcat -d -v threadtime >"$logcat_file" 2>&1 || true
echo "PASS: release process readiness, crash/ANR scan, and selected R8 WebView/JNI instrumentation checks completed; production signing is checked separately."
