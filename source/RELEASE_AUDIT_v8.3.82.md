# Rovex v8.3.82 Audit

## Source change

`SettingsActivity` no longer uses `lifecycleScope` even though it extends `android.app.Activity`. It now owns a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` and cancels it from `onDestroy()`. The neural test still switches to `Dispatchers.IO` for inference and returns to Main for UI.

## Required checks

- Version: versionCode 180 / versionName 8.3.82.
- Package/applicationId remains `com.localqbank.library`.
- Neural dependency remains pinned to `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- Zstandard remains `com.github.luben:zstd-jni:1.5.7-16@aar`.
- No model weights are bundled.
- No `runBlocking`, `Thread.sleep`, or `GlobalScope` introduced by this fix.
- Per-layout duplicate XML IDs are the only duplicate-ID rule.
- CI retains zstd arm64 ABI and signing certificate checks.
- Full Android Gradle compilation must be re-run in CI; this environment must not claim it is green without an actual successful Gradle build.
