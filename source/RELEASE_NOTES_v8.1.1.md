# Rovex v8.1.1 — Stability Hotfix

## Critical fixes
- Replaced `QBankDb` `PRAGMA foreign_keys=ON` via `execSQL()` with `SQLiteDatabase.setForeignKeyConstraintsEnabled(true)` to prevent startup `SQLiteException` on SQLite builds that return rows from PRAGMA statements.
- Replaced the equivalent unsafe Flashcard DB foreign-key PRAGMA with the Android API.
- Removed unsafe `PRAGMA journal_mode=WAL` execution through `execSQL()` and use Android WAL API instead.
- Added `PrefsCompat` to defensively read and migrate legacy numeric `SharedPreferences` values. This prevents `Integer cannot be cast to Float` / `Integer cannot be cast to Long` crashes after upgrades.
- Applied compatibility reads to Quiz typography preferences, progress timing, resilience heartbeat, backup timestamp, study-session timestamp, and adaptive-engine numeric state.
- Hardened `AdaptiveTypographyManager` so minimum and maximum auto-size values can never collapse to the same value, preventing the observed Android `IllegalArgumentException`.

## Audit status
- Source audit: completed for the supplied v8.1.0 source tree.
- XML/findViewById type audit: completed; no incompatible XML widget type was found. Custom Rovex view casts match their XML declarations.
- SQLite PRAGMA audit: completed for application Kotlin sources; remaining PRAGMAs are read through `rawQuery()` only.
- SharedPreferences numeric-type audit: completed for all direct Float/Long reads found in the application sources.
- Build verification: attempted locally, but Gradle 8.11.1 could not be downloaded because this environment has no network/DNS access. The source was therefore not falsely marked as compiled.
