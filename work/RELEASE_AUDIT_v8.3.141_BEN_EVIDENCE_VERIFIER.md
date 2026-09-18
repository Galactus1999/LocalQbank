# Rovex v8.3.141 Release Audit — Ben Evidence Verifier v1

## Baseline
- v8.3.140 / versionCode 238
- New version: v8.3.141 / versionCode 239
- applicationId remains `com.localqbank.library`

## Source change audit
Changed only:
1. `app/build.gradle.kts` — versionCode/versionName
2. `BenCognitiveArchitecture.kt` — evidence-aware verifier and fail-closed neural verification
3. `BenGroundedNeuralPipeline.kt` — typed evidence, citation-aware prompt, deterministic fallback on verifier rejection
4. `BenNeuralTelemetry.kt` — verifier metrics
5. `SettingsScreen.kt` — live verifier metrics
6. `BenAnswerVerifierTest.kt` — focused JVM tests
7. `.github/workflows/android-build.yml` — stale v8.3.140/versionCode 238 assertions updated to v8.3.141/versionCode 239
8. release notes/audit documents

No EmbeddingGemma runtime, Qualcomm native libraries, APKG importer, SRS, backup, flashcard, theme, notes, or manager ownership code was intentionally changed.

## Verifier audit
- No clinical contradiction/NLU engine added.
- Numeric gate applies only when grounded evidence is supplied.
- Neural callers cannot bypass the verifier by disabling the Adaptive Engine verifier toggle: `modelUsed && evidence.isNotEmpty()` forces verification.
- Invalid citation id fails closed.
- Critical numeric claim without nearby evidence citation fails closed.
- Critical numeric value absent from cited evidence fails closed.
- Unit conversions are explicit and finite rather than open-ended.
- Rejected neural draft is discarded; deterministic fallback is returned.
- Legacy `repair()` remains only as a compatibility helper and is not used to rescue a failed neural draft.

## Pure-Kotlin validation
A standalone extraction of `BenAnswerVerifier` was compiled with `kotlinc` and exercised successfully for:
- exact supported age
- wrong age
- invented citation
- missing citation
- 14 days ↔ 2 weeks
- 1000 mg ↔ 1 g
- 1000 mg/kg ↔ 1 g/kg

Result: PASS.

## Android compilation
Attempted:
`./gradlew :app:compileDebugKotlin --offline --no-daemon`

Result: BLOCKED before compilation because the wrapper attempted to obtain Gradle 9.3.1 from `downloads.gradle.org` and DNS could not resolve the host. No claim of Android compile success is made.

## Whole-project static checks
- 28 XML files parsed successfully.
- 0 XML parse errors.
- 0 duplicate IDs within individual layout files.
- 77 existing `findViewById<T>()` call sites inventoried; this change adds none and changes no XML widget types.
- Changed Kotlin startup/runtime paths were manually inspected; no new Activity `onCreate` work, native loading, database handles, or lifecycle-blocking operations were introduced by this verifier stage.

## XML / startup / findViewById
No XML files changed. Existing project-wide audit remains required before release/CI. The SettingsScreen text-only telemetry change does not add view ids or `findViewById` calls.

## CI gates still required
- Android compile/test green
- persistent signing certificate fingerprint assertion
- final APK zstd AAR/native assertion
- Qualcomm runtime packaging assertion
- whole-project source/runtime audit
- XML parse and per-layout duplicate-ID audit
- findViewById/XML type audit
- startup/onCreate runtime-risk audit
- ZIP integrity

## Release disposition
SOURCE-ONLY / NOT CI-GREEN until CI passes.
