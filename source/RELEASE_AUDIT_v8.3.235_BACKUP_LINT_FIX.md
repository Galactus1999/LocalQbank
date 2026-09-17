# Rovex v8.3.235 — Release Lint Backup Rules Correction

## Root cause
GitHub Actions run #712 reached `:app:lintVitalRelease` after the release compilation stage. Lint reported 9 fatal `FullBackupContent` errors in `backup_rules.xml` and `data_extraction_rules.xml`.

The rules used explicit `<include>` entries, but also attempted to exclude `qbank.db` even though that database was not in an included path. They also used `domain="cache"`; Android backup XML does not support `cache` as an exclude domain, and cache is already excluded by the backup system.

## Correction
- Removed redundant `database/qbank.db` excludes from both backup configurations.
- Removed invalid `cache` excludes from both backup configurations.
- Preserved the intended backup allow-list: `progress.xml`, `ui.xml`, and `progress_backups/`.
- Preserved separate cloud-backup and device-transfer rules.
- Bumped versionCode 328 -> 329 and versionName 8.3.234 -> 8.3.235.

## Validation
- XML parse: required for all XML resources.
- Duplicate IDs: checked per individual layout file.
- Backup-rule domains: only supported domains remain.
- No signing material added to source.
- CI release signing workflow artifact label updated to v8.3.235.

Android documentation confirms that the legacy full-backup domains are file/database/sharedpref/external/root, while the Android 12+ data-extraction format uses the same supported domain set inside cloud-backup/device-transfer. Android also excludes cache directories from backup behavior.

CI remains the authoritative release-build gate.
