# Rovex Android Visual Phase 3 — Screen-Level Visual Truth

## Purpose

Validate rendered Android UI after the Home/theme foundation and wallpaper/surface work. A successful compile or unit-test run is not visual acceptance. This phase must catch clipped, obscured, low-contrast, oversized, or theme-inconsistent UI using real emulator/device renders.

## Scope

Run the same screen matrix in Light, AMOLED, Mint, Sunset, Lavender, and Pastel:

1. Home dashboard: app header, search, clinical hero, Today Progress, Study Tools, Today Solved, QBank/flashcard cards.
2. QBank catalogue: source names, selected/in-progress source, empty/loading/error states, long names.
3. Question solving: question stem, options, selected/correct/incorrect states, explanation and images.
4. Flashcards: front/back, header/progress, Mark and Bookmark controls, image zoom, long content.
5. Settings/theme selection: selected theme, wallpaper selection, Glass/Normal preference, readable labels.
6. Dialogs/popups and floating navigation: import feedback, confirmation/error states, overlapping controls and system insets.

## Required evidence

- Capture screenshots from the generated APK using Android UIAutomator/emulator instrumentation; do not substitute mockups or manually drawn previews.
- Record APK/source revision, device/emulator resolution and density, Android version, theme, screen/state, and screenshot checksum.
- Retain screenshot artifacts in CI for review.
- Compare the current candidate with the approved baseline and inspect the actual pixels for clipping, wrong colors, missing assets, contrast failures, unexpected translucent washes, and layout regressions.
- Verify system bars, display cutouts, gesture/navigation insets, scrolling, and long text.
- Exercise both Glass and Normal surface modes where available.
- Run existing JVM, instrumentation, debug/release build, package identity, signing-fingerprint, and artifact-integrity gates. A visual failure must fail the visual gate even when compilation passes.

## Pass/fail criteria

- No clipped, hidden, overlapping, or unreachable primary content.
- QBank/source names and selected states remain legible in every supported theme.
- Primary and muted text meet the repository's contrast thresholds; do not infer contrast from token names alone.
- Wallpaper remains visible at intended opacity; surfaces—not a global white wash—provide local text readability.
- Controls remain tappable and correctly positioned; no regression to persisted user choices.
- Home Today Progress remains within its established 190dp acceptance limit.
- Screenshot capture and assertions must be deterministic enough to diagnose failures; known platform rendering variance must be explicitly bounded rather than silently ignored.

## Execution order

1. Establish a reproducible screenshot baseline from the latest green source archive.
2. Capture and inspect the full theme × screen matrix.
3. Produce a defect list tied to screenshot, screen/state, theme, and source location.
4. Fix the highest-impact shared root cause first; avoid screen-specific color patches when a semantic token or shared component is responsible.
5. Re-run the exact failing screenshot cases, then the full matrix and all release gates.
6. Report STATIC REVIEW, STATIC AUDIT, UNIT TEST, INSTRUMENTED TEST, BUILD, CI, and DEVICE evidence separately. Never claim physical-device verification from emulator output.

## Explicit non-goals

- Do not redesign unrelated business logic or introduce parallel theme/manager owners.
- Do not add continuous full-screen animation or unbounded bitmap decoding.
- Do not merge this branch or claim visual acceptance until CI evidence and rendered screenshots have been reviewed.
- Emulator evidence is not physical-device evidence; record device validation as BLOCKED until a real-device run is available.
