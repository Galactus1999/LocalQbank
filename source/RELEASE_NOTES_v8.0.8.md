# Rovex v8.0.8 — Notes / Ren Search Refinement

## Notes UX
- Removed visible per-note source/question/date metadata from the normal reading surface.
- View Question and Delete actions are hidden during normal reading and exposed by long-pressing a note.
- Long-press action sheet uses the Rovex visual theme rather than plain dark text controls.
- Existing explicit multi-select mode remains available for bulk deletion.

## Ren cognition / QBank search
- Ren now uses the same full-text QBank search index as the dedicated Search screen.
- Search covers question text, explanations, options and test metadata rather than relying primarily on subject/source labels.
- High-confidence clinical terminology expansion was added for common variants such as cancer/tumor/tumour/neoplasm/malignancy, heart attack/myocardial infarction, stroke/cerebrovascular accident and heart failure/cardiac failure.
- The Open Matching Questions action now uses the exact same ranked Ren search results, preventing the UI from displaying one set of matches while opening another.
- This specifically addresses the reported behaviour where a query such as “cancer marker” could surface an unrelated paediatric X-ray question.

## Branding
- Corrected the remaining user-facing “BEN INTELLIGENCE ORCHESTRATOR” label to “REN INTELLIGENCE ORCHESTRATOR”. Legacy migration markers are retained only for compatibility with previously stored notes.

## Toolchain
- AGP 8.10.1 + Gradle 8.11.1 + JDK 17 + compileSdk/targetSdk 36 retained.

## Verification limitation
- Source/static audits were performed locally.
- Android APK compilation could not be performed locally because Gradle 8.11.1 is not cached and services.gradle.org is unreachable in this environment.
- GitHub Actions remains the authoritative compile verification.
