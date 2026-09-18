# Rovex v8.3.7

## Backup and restore hardening
- Added a manual Full Backup containing both QBank and flashcard SQLite databases.
- Full backups include all app SharedPreferences except backup transport state and durable app-owned files such as flashcard media and knowledge data.
- Full backups use SQLite `VACUUM INTO` snapshots to safely capture WAL-backed databases.
- Added per-file SHA-256 checksums and a manifest to detect corruption or tampering before restore.
- Full restore is staged and applied only during a fresh app process, avoiding replacement of databases while Activities/managers hold SQLite handles.
- Full restore validates package, format, file sizes, checksums, and required databases before staging.
- Existing database files are protected by a pre-restore rollback copy during application.
- Added Full Backup create/export/upload and Full Restore UI actions.
- Existing lightweight automatic progress backup remains unchanged.
- Existing Google Drive/SAF folder integration remains optional; Google sign-in is not required.
