# Rovex v8.3.123 — Flashcard MVVM Deep Correction Audit

## Baseline
- Input baseline: v8.3.122 / versionCode 220
- Release candidate: v8.3.123 / versionCode 221
- applicationId: `com.localqbank.library`
- Scope: FlashcardActivity MVVM stabilization plus whole-project safety/source audit.
- No EmbeddingGemma/Gemma integration changes.

## Flashcard MVVM result
PASS
- `FlashcardActivity` uses `FlashcardViewModel` through the existing manual `RovexAppContainer` factory.
- `FlashcardActivity` has zero direct `QBankDb`, `FlashcardDb`, `ProgressStore`, `SharedPreferences`, or `getSharedPreferences()` access.
- `FlashcardReviewRepository` remains the screen persistence boundary.
- `SqliteFlashcardReviewRepository` owns the FlashcardDb handle for the ViewModel lifetime.
- `FlashcardUiState` is StateFlow-backed and contains screen/session data only; it contains no Android Views or Context.
- `FlashcardScheduler`/authoritative scheduling logic was not duplicated or replaced.
- AppContainer wiring remains manual; no Hilt/Dagger/Compose migration.
- Existing UI/import/navigation behavior remains Activity-owned.

## Deep correction applied
`FlashcardViewModel` was hardened after inspecting the actual v8.3.122 source:
- Serialized repository operations with a coroutine `Mutex` so repeated `onResume`/state-triggered refreshes and mutations cannot overlap against the same SQLite repository instance.
- Centralized state loading in `readState()`.
- Clear loading/error transitions on each operation.
- Settings save, unsuspend, delete-deck, and delete-tree now update state from the same serialized repository operation rather than launching a second nested refresh.
- Mutation callbacks are dispatched on `Dispatchers.Main.immediate`.
- Failure paths preserve the last valid state while exposing an error and no longer leave the screen stuck in a loading state.
- Added Stage 7 instrumentation coverage for FlashcardViewModel creation and state loading.

## Static/deep audit
PASS — Kotlin source scan: 122 production Kotlin files inspected; 148 Kotlin files including tests/instrumentation.
PASS — XML parse: 28 XML files parsed successfully.
PASS — per-layout duplicate IDs: no duplicate IDs within any individual layout.
PASS — findViewById/XML widget type audit: 0 mismatches.
PASS — `FlashcardActivity` direct persistence access: 0 direct DB/Prefs accesses.
PASS — main-source `runBlocking`: 0.
PASS — main-source `GlobalScope`: 0.
PASS — main-source `Thread.sleep`: 0.
PASS — main-source broad `catch(Throwable)`: 0.
PASS — unsafe SQLite `execSQL("PRAGMA ...")`: 0.
PASS — changed Kotlin brace/parenthesis/bracket balance.
PASS — manager ownership regression review; authoritative managers remain intact.
PASS — zstd Android AAR remains `com.github.luben:zstd-jni:1.5.7-16@aar`.
PASS — CI source retains ARM64 zstd assertion `lib/arm64-v8a/libzstd-jni-1.5.7-16.so`.
PASS — persistent signing certificate SHA-256 assertion remains in CI.
PASS — QNN/SentencePiece/EmbeddingGemma protection remains present.
PASS — Stage 7 instrumentation infrastructure remains present.
PASS — Stage 8 architecture enforcement remains present.
PASS — applicationId unchanged.
PASS — version identity updated monotonically: 220 -> 221; 8.3.122 -> 8.3.123.
PASS — no active CI identity assertion remains on the old 8.3.122/220 release values.
PASS — source ZIP contents and CRC integrity verified after packaging.

## Build truth
NOT VERIFIED locally.
Attempted:
`./gradlew --offline :app:compileDebugKotlin`

The wrapper attempted to obtain Gradle 9.3.1 from `services.gradle.org`, but DNS resolution failed in the execution environment. Therefore:
- Android compilation is NOT claimed green.
- Instrumented/device tests are NOT claimed passed.
- GitHub Actions remains the authoritative build/runtime gate.
- APK signing, packaged zstd native library, and device runtime remain CI-only validation items.

## Explicitly not changed
- EmbeddingGemma/Gemma 3 integration.
- AI architecture/governor ownership.
- FlashcardStudyActivity review-session implementation.
- SRS business ownership.
- AppManagers and authoritative engine/manager ownership.
- Existing corrected Rovex features.
