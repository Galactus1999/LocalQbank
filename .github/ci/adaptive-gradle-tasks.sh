#!/usr/bin/env bash
set -euo pipefail
PROJECT="$ADAPTIVE_PROJECT"
MODULE="$ADAPTIVE_MODULE_PATH"
cd "$PROJECT"
chmod +x gradlew
TASKS="$RUNNER_TEMP/adaptive-gradle-tasks.txt"
./gradlew tasks --all --no-daemon --console=plain > "$TASKS" 2>&1

probe() {
  ./gradlew "$1" --dry-run --no-daemon --console=plain >/dev/null 2>&1
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
  printf 'ADAPTIVE_UNIT_TASK=%s\n' "$UNIT_TASK"
  printf 'ADAPTIVE_DEBUG_TASK=%s\n' "$DEBUG_TASK"
  printf 'ADAPTIVE_ANDROID_TEST_TASK=%s\n' "$ANDROID_TEST_TASK"
  printf 'ADAPTIVE_RELEASE_TASK=%s\n' "$RELEASE_TASK"
  printf 'ADAPTIVE_HAS_ANDROID_TEST=%s\n' "$has_android_test"
} >> "$GITHUB_ENV"
{
  printf 'unit_task=%s\n' "$UNIT_TASK"
  printf 'debug_task=%s\n' "$DEBUG_TASK"
  printf 'android_test_task=%s\n' "$ANDROID_TEST_TASK"
  printf 'release_task=%s\n' "$RELEASE_TASK"
  printf 'has_android_test=%s\n' "$has_android_test"
} >> "$GITHUB_OUTPUT"

echo "Adaptive Gradle task selection:"
echo "  unit: $UNIT_TASK"
echo "  debug: $DEBUG_TASK"
echo "  androidTest: $ANDROID_TEST_TASK"
echo "  release: $RELEASE_TASK"
