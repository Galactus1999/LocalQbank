# Rovex v8.3.122 — Flashcard MVVM Stabilization Audit

## Scope
Extracted the Flashcard library screen's persistence/session-facing work from `FlashcardActivity` into a ViewModel-scoped repository and `FlashcardViewModel`.

## Architecture changes
- Added `FlashcardReviewRepository` contract.
- Added `SqliteFlashcardReviewRepository` implementation wrapping the existing authoritative `FlashcardDb`.
- Added `FlashcardUiState` + `FlashcardViewModel` + `FlashcardViewModelFactory`.
- Registered `flashcardViewModelFactory()` in `RovexAppContainer`.
- Converted `FlashcardActivity` to `AppCompatActivity` and wired `by viewModels { AppContainer... }`.
- Replaced Activity-owned FlashcardDb access with StateFlow-driven rendering and ViewModel operations.
- Database reads/writes for the library screen now execute through the ViewModel/repository on `Dispatchers.IO`.
- Existing `FlashcardDb` remains authoritative for SRS and persistence; no duplicate scheduler/business-logic owner was introduced.
- Added Flashcard factory wiring assertion and Activity recreation coverage to Stage 7 instrumentation.

## Verification
- Architecture regression audit: PASS
- XML parse: PASS (26/26)
- Per-layout duplicate IDs: PASS (0 duplicate-ID layouts)
- FlashcardActivity direct DB/Prefs access: PASS (0)
- Forbidden coroutine/thread patterns in main source: PASS (0)
- Broad `catch(Throwable)` in main source: PASS (0)
- Source brace/parenthesis balance for changed Kotlin files: PASS
- Version identity: 8.3.122 / versionCode 220
- CI workflow source identity and monotonic update gate: PASS (220 > 219)
- zstd Android AAR dependency preserved
- SentencePiece dependency preserved

## Build limitation
Local Gradle compilation was attempted with `./gradlew :app:compileDebugKotlin --offline`, but the Gradle wrapper attempted to download Gradle 9.3.1 and DNS resolution for `services.gradle.org` failed. Therefore Android compilation and instrumentation are **not claimed green**. GitHub Actions remains the authoritative build/device gate.

## Risk notes
- `FlashcardStudyActivity` remains outside this extraction and still owns its existing review-session implementation. It was deliberately not rewritten in this change.
- `FlashcardActivity` remains responsible for rendering, dialogs, animations, touch/UI behavior, imports, and navigation.
- No AI/EmbeddingGemma/Gemma integration was changed.
