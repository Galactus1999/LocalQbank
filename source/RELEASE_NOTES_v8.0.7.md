# Rovex v8.0.8 — Stability / UX / Import / State Integrity

## Core reliability
- Normalised question stable-key generation when `source_question_id` is null.
- This fixes progress/bookmark/Performance Lab lookups for Marrow-style imported questions.
- Collection screens refresh from the canonical progress snapshot when returning from Quiz.
- Performance Lab is scoped to the currently active QBank when a study session identifies it.

## Notes
- Removed automatic note creation after every wrong answer.
- Notes remain explicit user actions.
- Existing Android text selection saves only the selected substring.
- Added multi-select and bulk-delete for Notes.
- Note metadata now includes source QBank, subject, test and timestamp.

## Today's Revision
- Uses a deterministic per-day shuffle of wrong + bookmarked questions.
- The selection changes with the local calendar day while remaining reproducible within the day.

## Flashcards
- Wrong-question solving no longer creates notes automatically.
- Important bookmarking no longer creates a flashcard as a side effect.
- SRS settings use resize/scroll behaviour so fields remain accessible while typing.
- Three-dot reviewer controls use the existing visual-sheet design rather than plain AlertDialog lists.
- Swipe navigation uses a deliberate threshold and short transition lock to reduce accidental skips.

## Visual system
- Extended the lightweight Rovex flowing headline treatment to major headings.
- Added bounded touch feedback to major home/study surfaces.
- Reworked adaptive typography around Android native auto-size for constrained labels.
- Study Tool titles are given a larger readable base size with adaptive bounds.
- Launcher now uses an Android adaptive icon with a centered, gradient-coloured R foreground. The launcher icon itself cannot be continuously animated by the app/Android launcher; the flowing treatment is therefore used inside the app.

## HTML QBank import
- Added a determinate overall import progress indicator.
- Marrow/srcdoc imports show parsed sections with individual completion percentages and question counts.
- Streaming parser remains in place to avoid loading the full outer HTML into RAM.

## Verification limitation
- Local APK compilation could not be completed in this environment because Gradle 8.11.1 was not cached and outbound access to services.gradle.org was unavailable.
- GitHub Actions must perform the authoritative compile/device verification before calling the release compiled.
