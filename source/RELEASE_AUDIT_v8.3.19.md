# Rovex v8.3.19 Release Audit

## QBank loading / responsiveness
- Added `QBankLoadingEngine` as a dedicated metadata loading layer. Subquestion-bank lists are loaded off the UI thread and cached briefly; question HTML is not materialized while opening the section list.
- `TestListActivity` no longer loads the entire global `QuestionRef` list just to calculate section progress. It queries only the selected source and uses one in-memory `ProgressSnapshot`.
- Loading UI is immediate and explicitly says what is being loaded instead of appearing frozen.
- Quiz question cache was expanded and adaptive prefetch now uses the effective battery-aware prefetch depth.

## Import progress / crash perception
- Database import now reports question-level progress every 25 questions and updates the visible status with the current phase and count.
- WebView/JavaScript fallback extraction uses an indeterminate progress state while extraction is running, then switches to determinate progress for database writing.
- JavaScript bridge parsing and database insertion are kept off the UI thread; only UI updates are posted to the main thread.
- Large-file streaming reports staging, streaming and imported-question activity without holding the full file in memory.

## QBank metadata editing
- Main QBank names are editable without changing the stable imported file identity.
- Main QBank series number is editable.
- Subquestion-bank names and series numbers are editable by long-pressing a subquestion bank.
- Metadata is persisted in SQLite and survives backup/restore.
- Existing databases are migrated in place with additive columns only.

## Adaptive Engine / Battery Engine
- Battery Engine is now explicitly visible inside the Adaptive Engine settings panel.
- It remains a policy/governor layer only and does not own QBank business logic, scheduling, wake locks, system settings, or foreground blocking.

## Stability audit
- package/applicationId remains `com.localqbank.library`.
- versionCode/versionName bumped monotonically to 117 / 8.3.19.
- compileSdk 37 / targetSdk 36 preserved.
- zstd Android AAR preserved.
- XML parsing and per-layout duplicate-ID audit passed.
- `findViewById<T>()` vs XML widget-type audit passed.
- no `Thread.sleep`, `runBlocking`, `GlobalScope`, or process-killing code.
- no broad `catch(Throwable)` / `catch(Error)`.
- no unsafe SQLite PRAGMA via `execSQL`.
- provenance Ed25519 signature verified.

## Build verification
- Local Gradle compilation remains unavailable in this environment because Gradle 9.3.1 cannot resolve `services.gradle.org` via DNS. GitHub CI remains the authoritative APK compilation and native-zstd packaging check.
