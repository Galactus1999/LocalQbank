# Rovex v8.3.82

## Build-fix release

- Fixed the v8.3.81 CI Kotlin compilation failure in `SettingsActivity`: `lifecycleScope` was used on a platform `android.app.Activity`, which is not a `LifecycleOwner`.
- Replaced that invalid lifecycleScope use with a dedicated, cancellable activity coroutine scope. The scope is cancelled in `onDestroy()`, so the one-shot neural test cannot outlive the Activity.
- Kept neural generation CPU-first, one-shot, governor-gated, bounded, and optional. No normal Ben answer path is routed through the neural model yet.
- Removed duplicate LiteRT-LM and lifecycle-runtime dependency declarations.
- Bumped versionCode 179 -> 180 and versionName 8.3.81 -> 8.3.82.

## Stability gate

The supplied CI log reached `:app:compileDebugKotlin` and failed only on the invalid `lifecycleScope` receiver in `SettingsActivity.kt:419`. This source revision addresses that exact compiler error. A fresh Android Gradle build is still required to mark compilation green.
