# Rovex v8.3.10 Release Audit

- Baseline: v8.3.7, versionCode 105
- Release: v8.3.10, versionCode 108
- Purpose: backup/restore UX clarification and complete full-restore durable-file restoration.

## Backup UX
- Backup & Restore now explains the difference between Automatic/Progress Backup and Full Backup.
- Includes a concrete one-month-use → uninstall → reinstall scenario.
- Explicitly warns that private automatic local snapshots can be removed by uninstall.
- Gives a simple recommended routine: automatic snapshots + external backup folder + occasional Full Backup.

## Full Backup / Restore correctness
- Full backup contains qbank.db, flashcards.db, non-transport SharedPreferences, and durable app-owned files.
- Full restore verifies manifest, file sizes and SHA-256 hashes before staging.
- Full restore is applied before manager/database startup on next process launch.
- Full restore now restores the durable `files/` tree, including flashcard media/knowledge represented there.
- Existing durable files not present in the full snapshot are removed during restore so stale data does not survive.
- Pre-restore database snapshots use SQLite `VACUUM INTO` rather than raw DB-file copies.
- File, database and preference rollback is attempted if restore fails.

## Safety / architecture
- `backup_settings.xml` remains transport state and is not restored.
- Automatic progress backup remains intentionally small and does not contain the QBank database or full flashcard collection.
- QBank HTML source is not uploaded by backup.
- No Google Sign-In dependency was introduced; Android's document picker/SAF remains the external-folder mechanism.
- No fatal `Error` swallowing was introduced in the new restore path.

## Static checks
- Modified Kotlin files: balanced braces/parentheses.
- Package/applicationId remains `com.localqbank.library`.
- VersionCode is monotonic: 106.
- VersionName is 8.3.10.

## Additional crash-hardening pass
- Narrowed non-fatal recovery handlers from `Throwable` to `Exception` so OOM, linkage failures and other fatal VM/runtime errors are not silently swallowed.
- APKG per-card tolerance now skips malformed card exceptions without swallowing fatal `Error` subclasses.
- CI version assertions were corrected to the actual v8.3.9/versionCode 107 release.

## v8.3.10 correction pass
- Removed a duplicate `uploadFullBackup(File)` declaration that would cause a Kotlin compile-time conflicting-overload error.
- Fixed BackupActivity progress-backup restore path: the worker thread was constructed but not started, so tapping RESTORE PROGRESS BACKUP could appear to do nothing.
- Re-ran XML, view-type, duplicate-ID, broad-catch, blocking-call, PRAGMA, and version-consistency audits.
