# Rovex v8.3.262 — AI Handoff, Home Search, PDF Context & UI Stabilization

## Changes
- Ben + AI no longer repeats the question, options, or QBank explanation in its visible result surface.
- Ben + AI now uses Ben as a bounded local-context collector and lets configured Free AI take the lead on the answer.
- Frankenstein BEN + FREE AI follows the same context-then-handoff flow and does not inject old chat continuity into the Free AI prompt.
- Home search now returns QBank/sub-QBank hierarchy entries as well as question matches.
- Settings Search is scoped to settings only; the old general search route is no longer used by the Settings menu.
- Added a Settings entry for local reference PDF import. Existing Ben PDF OCR/retrieval is reused, and retrieved PDF excerpts are included in the compact Free AI handoff.
- Reworked Home Flashcards and Performance Lab cards to use robust horizontal layouts instead of constrained/translated overlays.
- Reduced the Home header height and tightened decorative header views.
- Improved Free AI Markdown rendering contrast, paragraph readability, table borders/padding, wrapping and dark/light theme adaptation.
- Bumped version to 8.3.261 / versionCode 356.

## Validation
- Ruthless static audit: PASS
- Architecture regression audit: PASS
- Local Android Gradle compile: NOT VERIFIED; Gradle 9.3.1 distribution download is blocked by DNS in the current environment.
- GitHub Actions remains the required green-build gate.

- Permanent compile-safety correction: SettingsScreen muted text rendering is now a class-level helper, eliminating cross-method local-function scope errors.
- Static audit now rejects unqualified `muted(...)` calls in SettingsScreen so this exact defect is caught before CI compilation.
