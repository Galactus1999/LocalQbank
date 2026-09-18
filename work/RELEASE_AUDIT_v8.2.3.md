# Rovex v8.2.3 — AMOLED Black cleanup

- AMOLED neutral/background surfaces are pure `#000000`.
- Removed gray/green/yellow/blue tint from neutral AMOLED panels, cards, options, metrics, dialogs, search surfaces, and flashcard neutral surfaces.
- Deliberate semantic colored sections remain colored: hero/primary actions, correct/wrong states, bookmark categories, semantic quick actions, and other explicitly colored controls.
- Text, muted text, accents, and borders remain visible and are not forced to black.
- Fixed MainActivity source-list text to use theme-aware text/muted colors in AMOLED.
- Preserved the manual-only flashcard policy: wrong questions never automatically become flashcards.
- Version: 8.2.3 (versionCode 91).

## Stability gate
- No unsafe `execSQL("PRAGMA ...")` pattern found.
- No native global auto-size configuration found.
- No `createFromWrongQuestions()` path found.
- Existing XML/view-type audit remains compatible; custom `RovexColorFlowTextView` subclasses `TextView`, so TextView lookups are safe.
- Local compilation cannot be completed in this environment because Gradle 8.11.1 is not cached and external Gradle distribution DNS/network access is unavailable. CI compilation remains mandatory before installation.
