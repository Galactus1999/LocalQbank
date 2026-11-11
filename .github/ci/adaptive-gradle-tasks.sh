#!/usr/bin/env bash
set -euo pipefail
PROJECT="$ADAPTIVE_PROJECT"
MODULE="$ADAPTIVE_MODULE_PATH"
cd "$PROJECT"
chmod +x gradlew
TASKS="$RUNNER_TEMP/adaptive-gradle-tasks.txt"
if ! ./gradlew tasks --all --no-daemon --console=plain > "$TASKS" 2>&1; then
  echo "::error::Gradle task discovery failed; captured wrapper output follows."
  tail -n 160 "$TASKS" || true
  echo "::group::Gradle wrapper diagnostics"
  ./gradlew --version --no-daemon --console=plain || true
  echo "::endgroup::"
  exit 1
fi

probe() {
  local task="$1"
  local safe_task
  safe_task="$(printf "%s" "$task" | tr -c "A-Za-z0-9" "_")"
  local log="$RUNNER_TEMP/gradle-probe-${safe_task}.log"
  if ./gradlew "$task" --dry-run --no-daemon --console=plain >"$log" 2>&1; then
    return 0
  fi
  echo "[adaptive-gradle] task probe failed: $task"
  tail -n 35 "$log" || true
  return 1
}

UNIT_TASK=""
for t in "$MODULE:testDebugUnitTest" "$MODULE:testReleaseUnitTest" "$MODULE:test"; do
  if probe "$t"; then UNIT_TASK="$t"; break; fi
done

DEBUG_TASK=""
for t in "$MODULE:assembleDebug" "$MODULE:assemble"; do
  if probe "$t"; then DEBUG_TASK="$t"; break; fi
done

ANDROID_TEST_TASK=""
for t in "$MODULE:assembleDebugAndroidTest" "$MODULE:assembleAndroidTest"; do
  if probe "$t"; then ANDROID_TEST_TASK="$t"; break; fi
done

RELEASE_TASK=""
for t in "$MODULE:assembleRelease" "$MODULE:assemble"; do
  if probe "$t"; then RELEASE_TASK="$t"; break; fi
done

[[ -n "$DEBUG_TASK" ]] || { echo "No usable Android assemble task discovered."; exit 1; }
[[ -n "$RELEASE_TASK" ]] || { echo "No usable release/assemble task discovered."; exit 1; }

has_android_test=false
if [[ -n "$ANDROID_TEST_TASK" ]]; then
  has_android_test=true
fi
{
  printf "ADAPTIVE_UNIT_TASK=%s\\n" "$UNIT_TASK"
  printf "ADAPTIVE_DEBUG_TASK=%s\\n" "$DEBUG_TASK"
  printf "ADAPTIVE_ANDROID_TEST_TASK=%s\\n" "$ANDROID_TEST_TASK"
  printf "ADAPTIVE_RELEASE_TASK=%s\\n" "$RELEASE_TASK"
  printf "ADAPTIVE_HAS_ANDROID_TEST=%s\\n" "$has_android_test"
} >> "$GITHUB_ENV"
{
  printf "unit_task=%s\\n" "$UNIT_TASK"
  printf "debug_task=%s\\n" "$DEBUG_TASK"
  printf "android_test_task=%s\\n" "$ANDROID_TEST_TASK"
  printf "release_task=%s\\n" "$RELEASE_TASK"
  printf "has_android_test=%s\\n" "$has_android_test"
} >> "$GITHUB_OUTPUT"

echo "Adaptive Gradle task selection:"
echo "  unit: $UNIT_TASK"
echo "  debug: $DEBUG_TASK"
echo "  androidTest: $ANDROID_TEST_TASK"
echo "  release: $RELEASE_TASK"
