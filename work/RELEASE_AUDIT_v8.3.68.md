# Rovex v8.3.68 Stability Audit

- versionName: `8.3.68`
- versionCode: `166`
- package/applicationId: `com.localqbank.library`
- Baseline: v8.3.67 peak-stability source.

## Bundled engineering slice

1. **Ben input hardening** — both scan work and output are hard-bounded to 4096 characters; whitespace is canonicalized and embedded control characters are removed while ordinary Unicode clinical text is retained.
2. **Ben malformed-input coverage** — added tests for control-only input and the hard bound.
3. **Flashcard drag-state hardening** — persisted Mark/Bookmark coordinates now reject non-finite saved values and non-finite drag coordinates instead of allowing NaN/Infinity to propagate into View geometry.
4. **Ben failure containment** — local backend and local clinical-engine failures now degrade to a safe user-facing result instead of propagating an exception into the Activity path.
5. **CI/release identity hardening** — version identity advanced to 8.3.68/166 and all release gates were updated consistently.

## Verification performed

- XML parsing: PASS.
- Active source version identity scan: PASS.
- Historical version references remain confined to historical release-audit documents.
- BenResearchInputPolicy standalone Kotlin compilation: PASS.
- MovableControlPosition standalone Kotlin compilation: PASS.
- BenResearchInputPolicy test source inspection: PASS.
- MovableControlPosition test source inspection: PASS.
- Existing Macrobenchmark metric/source audit: PASS.
- Existing JankStats source-set isolation audit: PASS.
- Existing findViewById/XML-type audit script remains present in CI: PASS.
- Full Gradle Android build: **not locally verifiable** because this environment cannot resolve the pinned Gradle 9.3.1 distribution from `services.gradle.org`; CI remains authoritative.
