# Rovex v8.3.214 — Deep Correction Audit

Baseline: v8.3.213 Cosmos/Avatar/Gemini source.

## Corrections
- Updated Gemini Search model from `gemini-3.8-flash` to `gemini-3.7-flash`, matching the currently documented Google Search grounding support set.
- Corrected Firebase App Check initialization so it is installed even when Firebase was auto-initialized before Ben first requests Firebase services.
- Kept Google authentication behind Credential Manager + Firebase Authentication and persistent Firebase user state.
- Made Frankenstein/Ben the final local orchestration/gating layer after Gemini web research; Gemini remains an external evidence collaborator, not the owner of QBank truth or study state.
- Added Cosmos/Pandora entries to the Settings theme selector; the main appearance menu already contained both themes.
- Serialized ProgressStore read/export/restore operations with the same process-scoped SQLite monitor used by answer mutations, closing a backup-consistency/read-modify-write race around `setMistakeType`, `allEntries`, `allProgressKeys`, and restore.
- Added an asynchronous local backup checkpoint when the app receives `TRIM_MEMORY_UI_HIDDEN`, tightening backup freshness without blocking lifecycle/UI work.

## Audit results
- Kotlin source files: 190
- XML resources: 28
- XML parse errors: 0
- Duplicate IDs within an individual XML layout: 0
- GlobalScope: 0
- Thread.sleep(): 0
- TODO/FIXME/NotImplementedError: 0
- Android Gradle compile: NOT VERIFIED in this environment because Gradle 9.3.1 download from `downloads.gradle.org` fails DNS resolution.

## Architecture
- AppManagers remains the authoritative manager owner.
- Deterministic local QBank/Graph-RAG remains authoritative.
- Gemini is explicit user-initiated cloud research and requires Google/Firebase authentication.
- Google Search grounding metadata/sources are retained and displayed; no cloud result silently becomes local QBank truth.
- EmbeddingGemma/NPU isolated-process architecture is unchanged.
- Progress SQLite remains synchronous/authoritative; backup is asynchronous durability support.
