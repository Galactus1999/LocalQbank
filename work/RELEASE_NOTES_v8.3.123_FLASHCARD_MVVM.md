# Rovex v8.3.123 — Flashcard MVVM Stabilization

- VersionCode: 221
- Package: `com.localqbank.library`
- Baseline: v8.3.122 / 220

## Changes
- Hardened the existing FlashcardActivity -> FlashcardViewModel -> FlashcardReviewRepository boundary.
- Serialized repository operations to prevent overlapping SQLite screen operations during lifecycle refreshes and mutations.
- Centralized FlashcardUiState loading.
- Improved loading/error state transitions.
- Removed nested refresh launches after settings/deck mutations.
- Kept FlashcardDb/SRS ownership authoritative.
- Added Stage 7 FlashcardViewModel state-loading instrumentation coverage.
- Preserved the existing flashcard UI, import workflow, navigation, themes, and study-session behavior.
- No EmbeddingGemma/Gemma integration changes.

## Validation
Static/source audits pass. Android compilation and device instrumentation remain unverified until CI because the current environment cannot resolve `services.gradle.org` to bootstrap Gradle 9.3.1.
