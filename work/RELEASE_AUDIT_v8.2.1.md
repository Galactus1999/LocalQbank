# Rovex v8.2.1 — stability audit

## Fixed in this audit
- Daily SRS override value `0` now correctly means "use normal daily limit".
- Review/day accounting now counts scheduled reviews (`old_interval > 0`) separately from new cards.
- Configurable initial interval, Hard multiplier, Easy multiplier and leech threshold are now applied by the SRS engine.
- Manual generated decks are labelled `Rovex Manual`; older `Adaptive` decks remain visible under the Manual compatibility filter.
- Removed stale "Generate adaptive cards" wording from Ren's explicit flashcard action.
- Flashcard settings backup now includes `today_override_date` and closes `FlashcardDb` deterministically.
- CI static audit now checks the historical unsafe-PRAGMA, global-auto-size and automatic-wrong-to-flashcard failure patterns.
- CI view-type audit accepts safe retrieval through `TextView`/`ImageView` superclasses for custom subclasses while still catching incompatible casts.

## Historical crash protections rechecked
- `QBankDb`: foreign keys use `setForeignKeyConstraintsEnabled(true)`; no `execSQL("PRAGMA ...")` startup path.
- Quiz numeric preferences use `PrefsCompat` to tolerate legacy Int/Float storage.
- Global adaptive typography remains a no-op; no native global auto-size configuration remains.
- `findViewById` XML/type audit passes with custom view superclass handling.
- No `createFromWrongQuestions()` path exists.

## Build status
Local Gradle compilation is unavailable in this environment because Gradle 8.11.1 cannot be downloaded (`services.gradle.org` DNS failure). CI compilation remains mandatory before installation/release.
