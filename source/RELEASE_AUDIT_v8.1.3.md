# Rovex v8.1.3 — Stability Audit

## Findings corrected
- Main dashboard **Today's Revision** button was incorrectly wired to the generic due collection instead of `startTodayRevision()`.
- Remote question images now use an explicit browser-like User-Agent, image Accept headers, redirects, and bounded decoding.
- Native HTML imports now resolve relative media URLs against the selected HTML URI before persistence.
- Adaptive typography has a final pixel-level guard so Android can never receive an equal/invalid autosize min/max pair.
- Launcher artwork replaced with the supplied REVEX logo artwork.
- Release version aligned to `versionCode 81` / `versionName 8.1.3`.

## Previous crash classes re-audited
- SQLite foreign-key setup uses `setForeignKeyConstraintsEnabled(true)`; no unsafe foreign-key PRAGMA via `execSQL`.
- WAL is enabled through `enableWriteAheadLogging()`; no `journal_mode=WAL` through `execSQL`.
- Legacy Float/Long SharedPreferences reads use `PrefsCompat` migration.
- Typed `findViewById<T>()` references were checked against XML widget classes: no mismatches found.
- AdaptiveTypographyManager no longer permits equal min/max autosize bounds.
- Large HTML imports remain streaming/bounded; the previous full-file large allocation path is not used for large imports.

## Build verification
The source was structurally audited and ZIP integrity was checked. Gradle compilation could not be completed in this environment because the configured Gradle 8.11.1 distribution is not locally cached and external DNS/network access is unavailable. Do not treat this as compile-verified until CI/device build succeeds.
