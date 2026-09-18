# Rovex v8.3.240 — Compile Recovery + Dashboard Relic Cards

## Critical correction
- Fixed the premature closing brace in `MainActivity.renderUi()` that moved the remainder of the method outside the class and caused the large cascade of `this`, `dp`, `startActivity`, `findViewById`, and top-level `override` compiler errors seen in CI.

## Dashboard UX
- Redesigned **Today’s Solved** and **Study Workbench** cards with lightweight, theme-aware paper/relic vector decoration.
- Added subtle calendar/check, open-book, and flashcard relic motifs.
- Added theme-aware borders, pressed-state shading, and ripple feedback.
- Kept the cards asset-free and density-independent.
- Added accessibility content descriptions.
- Restored a useful Study Workbench subtitle: revision, PYQ, tests and weak areas.

## CI / audit hardening
- Added a focused `MainActivity` ↔ `activity_main.xml` ID contract audit to `tools/ruthless_audit.sh`.
- Updated CI checkout/setup-java actions to current major versions to avoid the Node 20 deprecation path.
- Version bumped to 8.3.240 / versionCode 334.

## Verification
- XML parsing: PASS.
- Per-layout duplicate-ID audit: PASS.
- MainActivity `R.id` contract: PASS.
- Kotlin brace/structure audit: PASS.
- Full Android Gradle compile: NOT locally verified because `downloads.gradle.org` DNS is unavailable in this environment. GitHub Actions remains the build gate.
