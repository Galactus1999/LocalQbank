# Rovex v8.2.2 — deep stability audit

## Corrections
- Fixed SRS "Today only" semantics: an override of `0` now means "use the normal daily limit" as shown by the UI, rather than blocking all cards.
- Review/day accounting now counts only scheduled reviews (`old_interval > 0`); new-card first reviews remain governed by the new-card limit and the total-card cap.
- Initial interval, Hard multiplier, Easy multiplier and leech threshold are now actually consumed by the SRS review engine.
- Manual generated flashcard decks are now labelled `Rovex Manual`. Older decks created by previous builds remain accessible through the Manual compatibility filter.
- Removed stale Ren wording suggesting "Generate adaptive cards"; explicit flashcard creation is presented as a manual action.
- Flashcard settings backup now includes the temporary override date and closes the flashcard database deterministically.
- Backup restore now snapshots/restores flashcard settings transactionally along with progress/UI/notes, preventing a failed restore from leaving SRS settings partially changed.
- CI static audit was corrected to parse XML/view IDs properly, accept safe `TextView`/`ImageView` superclass retrieval for custom subclasses, and check historical startup-crash patterns.
- CI no longer incorrectly rejects the intentional cleartext setting required for legacy HTTP QBank image URLs; imported HTML remains restricted from universal file-URL access.

## Historical crash audit
- QBank SQLite: no `execSQL("PRAGMA ...")` path; foreign keys use the Android database API.
- Quiz preferences: legacy numeric type compatibility remains handled through `PrefsCompat`.
- Adaptive typography: global native auto-size configuration remains removed/no-op.
- `findViewById<T>()` audit: no incompatible XML/widget cast found; the previous `ImageView`→`TextView` failure pattern is explicitly guarded in CI.
- No automatic wrong-answer → flashcard creation path exists.
- No `Thread.sleep`, `runBlocking`, `GlobalScope`, or `killProcess` application paths found.

## Build status
Local Gradle compilation remains unavailable because Gradle 8.11.1 cannot be downloaded in this environment (`services.gradle.org` DNS failure). The source/static audit passes, but CI compilation must succeed before APK installation/release.
