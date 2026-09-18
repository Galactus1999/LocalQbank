# Rovex v8.3.242 — Theme / AI / Startup Integrity Hardening

## Implemented

- Removed the paper/relic dashboard card treatment. Today's Log, Study Workbench, Flashcards and Performance Lab now use transparent/theme-native surfaces.
- Removed the unused `RovexRelicCardDrawable` implementation.
- Expanded procedural dark-theme atmosphere with multiple shaded, cratered/ringed planets for Cosmos, Avatar, Midnight, Dark and AMOLED themes.
- Added a compact home-screen Performance Lab card with health/resilience metrics. The full analytical lab remains available from Settings.
- Added optional Performance Lab background mode: `THEME` uses the current theme atmosphere; `DATA` uses a color-graded analytical background.
- Added flashcard progress to the home-screen Flashcards card: total cards, reviews and due count.
- Made Settings and its detail `visualCard` surfaces transparent/theme-aware with accent borders.
- Added a dedicated Search entry to the Settings index.
- Hardened OpenRouter/Groq/DeepSeek API integration:
  - HTTPS-only provider endpoints.
  - User-Agent and JSON headers.
  - explicit non-stream request mode.
  - robust string/array content response parsing.
  - API-key length validation.
  - Save-and-test flow with failure containment.
  - provider is disabled after a failed connection test instead of leaving a broken enabled state.
  - provider diagnostics can test all enabled providers.
  - Android Keystore encrypted key storage retained.
- Added bounded startup integrity gate during the launcher splash. It checks core manager initialization, critical manager availability, provider contract validity and manifest activity availability without network calls or cold-starting neural inference.
- Startup health is persisted through `StartupHealthStore` and surfaced in the Diagnostics Center.
- Added generated Rovex silver/cursive launcher artwork to adaptive and legacy launcher resources.
- Removed `android:usesCleartextTraffic="true"`; current external AI endpoints are HTTPS.
- Refreshed the architecture regression baseline after intentional accepted architecture growth; large-file warnings remain visible.
- Version: 8.3.242 / versionCode 336.

## Verification

- `tools/ruthless_audit.sh`: PASS
- XML/resource parsing: PASS
- MainActivity layout-ID audit: PASS
- Kotlin structural brace audit for modified files: PASS
- Architecture regression audit: PASS
- Android Gradle compilation: NOT VERIFIED locally because `downloads.gradle.org` DNS is unavailable in the current environment. GitHub Actions remains the authoritative compile/release gate.
