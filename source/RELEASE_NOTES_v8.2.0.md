# Rovex v8.2.0 — Daily SRS + Automatic Backup

## SRS settings
- Replaced the broken/empty SRS dialog with a dedicated scrollable settings surface.
- Added normal daily limits: New cards/day, Reviews/day, Total cards/day.
- Added Today-only overrides for new, review, and total limits. Overrides expire automatically on the next calendar day.
- Retained scheduling controls: Again delay, initial interval, Easy/Hard multipliers, leech threshold.
- Added Reset and Save controls. Inputs are editable, validated and persisted to the flashcard database.
- Daily limits are enforced by the flashcard study-mode counters, including the total daily cap.

## Backup hardening
- Automatic startup backup is scheduled when the last backup is older than six hours.
- A 12-hour periodic automatic backup is also scheduled through WorkManager.
- Android 10+ automatically writes user-visible backups to `Downloads/Rovex/Backups`.
- Older Android versions use the app-specific `Documents/Rovex/Backups` directory without broad storage permission.
- Maintains a stable `Rovex_Progress_latest.qbackup` plus up to eight historical snapshots.
- Backup format upgraded to v3 and now includes flashcard/SRS settings.
- Existing checksum validation and two-phase restore are retained.

## Safety audit
- No `PRAGMA` statements executed via `SQLiteDatabase.execSQL`.
- No native global autosize calls.
- No invalid `selectAllOnFocus = ...` assignment.
- Changed Kotlin files have balanced delimiters after masking comments/strings.
- ZIP integrity verified.
- Gradle compilation could not be executed in this environment because Gradle 8.11.1 must be downloaded from services.gradle.org and DNS/network access is unavailable. CI compilation remains mandatory before installation.
