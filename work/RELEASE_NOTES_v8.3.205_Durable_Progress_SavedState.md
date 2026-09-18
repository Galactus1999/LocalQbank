# Rovex v8.3.205 — Durable Progress + Saved State Hardening

## Baseline
- Built directly from v8.3.204.
- v8.3.204 remains preserved as `Rovex_v8.3.204_BACKUP_DO_NOT_MODIFY.zip`.

## Changes
1. QBank quiz progress moved from write-path SharedPreferences to transactional SQLite `progress_v2` + `progress_meta` in the existing `qbank.db`.
2. Existing SharedPreferences progress is migrated once, with the legacy preferences intentionally retained as a rollback snapshot for one release.
3. Stable-key identity is preserved; no dependence on numeric question IDs is introduced.
4. SM-2 fields are persisted atomically with answer/status updates: attempts, lastAttempted, timeMs, mistake, easeFactor, repetitions, intervalDays, nextDue, bookmark and review.
5. Position/source-resume metadata is persisted transactionally.
6. Existing lightweight backup format remains compatible: `BackupManager` now serializes/restores the authoritative SQLite-backed progress repository rather than stale SharedPreferences.
7. `PerformanceManager` now builds its progress snapshot from the authoritative SQLite repository.
8. `QuizViewModel` now receives a `SavedStateHandle` through `CreationExtras` and persists small transient session state (test ID, position, practice key, exam timer/answers, session label). Durable study data remains SQLite-backed.

## Deliberately not changed
- Messenger/Binder IPC implementation.
- Proven EmbeddingGemma/NPU path.
- AICore/Gemini Nano generation path. AICore remains a future optional backend candidate, not an embedding replacement.
- FGS `specialUse` architecture. Existing deterministic neural fallback remains authoritative when FGS/neural generation is unavailable.

## Validation status
- Source/static audit required before release.
- Android Gradle compilation is UNVERIFIED in the current environment because `downloads.gradle.org` DNS is unavailable.
- Device validation is required before calling this release green.
