# Rovex v8.3.37 — Incremental Stability Slice

## Scope
Small, non-UI stability improvements on top of v8.3.36.

- Added `BackupArchivePolicy` as a pure, testable guardrail for full-backup ZIP extraction.
- Bounded archive entry count, individual entry expansion, total uncompressed expansion, and path length.
- Added debug-only Android StrictMode diagnostics for disk/network/SQLite/closable misuse.
- Added JVM tests for backup archive safety limits.
- No layout XML, color resources, style resources, or view IDs changed.
- No database schema migration.
- No Room/Compose/Hilt introduction.
- No replacement of authoritative managers.

## Build truth
Local full Android Gradle compilation is environment-dependent and must be verified by GitHub Actions. No local APK compilation is claimed unless the Gradle task actually completes.
