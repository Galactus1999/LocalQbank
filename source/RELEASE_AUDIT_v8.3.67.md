# Rovex v8.3.67 Stability Audit

- versionName: `8.3.67`
- versionCode: `165`
- package/applicationId: `com.localqbank.library`
- Baseline: v8.3.66 peak-stability source.

## Bundled engineering slice

1. **Ben input hardening** — bounded, canonical Unicode/whitespace normalization and removal of embedded control characters before local retrieval/backend dispatch.
2. **Flashcard control geometry hardening** — pure `MovableControlPosition` policy with unit tests; persisted Mark/Bookmark coordinates now scale safely when the card surface changes size/orientation and remain clamped to the visible surface.
3. **Flashcard accessibility/automation hooks** — Mark and Bookmark expose stable content descriptions without changing their existing touch/move behavior.
4. **CI identity/audit hardening** — version identity advanced to 8.3.67/165 and the workflow now asserts the new position-policy/test artifacts.

## Verification performed

- XML parse: PASS.
- BenResearchInputPolicy standalone Kotlin compilation: PASS.
- Whole-project stale active-version scan: PASS.
- Existing benchmark metric presence: PASS.
- CI source identity expectations updated: PASS by source inspection.
- Full Gradle test/build: **not locally verifiable** because the environment cannot resolve `services.gradle.org` to download the pinned Gradle 9.3.1 distribution. CI remains authoritative for Android compilation and instrumentation.
