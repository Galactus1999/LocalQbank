# Rovex v8.3.28 — Flashcard UI Redesign & Stability Audit

## Scope
Only the Flashcard main-section presentation was changed. Existing flashcard study, SRS, statistics, import, deck actions, filtering, and navigation behavior remain wired to their existing functions.

## UI changes
- Reworked the FlashcardActivity top section into a compact action board aligned with the Home screen's restrained palette and contrast.
- Kept high-frequency actions visible: Review Due, Bookmarked, and Study.
- Moved less-frequently used SRS Settings, Statistics, and APKG Import into a single visual **More tools** sheet.
- Reduced the Study launcher footprint while retaining all existing study modes and sprint limits.
- Replaced the oversized All / Manual / Imported controls with compact segmented chips.
- Fixed the filter-selection visual state: the selected chip now changes fill/text treatment immediately and remains synchronized with `deckFilter`.
- Replaced plain popup-list presentation with the existing rounded-card sheet system; More tools therefore opens as visual cards rather than plain text on a dark background.
- Deck list interaction and deck action sheet were not functionally changed.

## Source/runtime audit
- Workflow-compatible `findViewById` vs XML type audit: PASS.
- Per-layout duplicate XML ID audit: PASS.
- Broad `Throwable` catch audit: PASS.
- Unsafe SQLite PRAGMA audit: PASS.
- Native text autosize audit: PASS.
- Automatic wrong-answer -> flashcard path audit: PASS.
- Blocking `Thread.sleep` / `runBlocking` / `GlobalScope` audit: PASS.
- zstd Android AAR assertion preserved: PASS.
- Package/applicationId preserved: `com.localqbank.library`.
- targetSdk preserved: 36.
- compileSdk preserved: 37.
- Version monotonicity: `8.3.27`/125 -> `8.3.28`/126.
- Owner provenance Ed25519 signature verification: PASS.

## Build verification
Local Gradle compilation could not be performed because the environment cannot resolve `services.gradle.org` (DNS failure while attempting to obtain Gradle 9.3.1). GitHub CI remains the authoritative APK build path.
