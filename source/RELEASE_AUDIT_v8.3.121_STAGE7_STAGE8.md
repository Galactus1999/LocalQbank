# Rovex v8.3.121 — Stage 7 + Stage 8 Deep Audit

## Release identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.121`
- versionCode: `219`
- Baseline: v8.3.120 / versionCode 218

## Stage 1–6 stabilization status
- QuizActivity: presentation/session boundary retained; no direct QBankDb/ProgressStore/SharedPreferences ownership.
- MainActivity: ViewModel/repository boundary retained; no direct QBankDb/ProgressStore/SharedPreferences ownership.
- HtmlImportActivity: thin UI boundary around ImportPipelineCoordinator + operation-scoped HtmlImportRepository; streaming large-file path retained.
- SettingsActivity: thin host + SettingsViewModel/SettingsScreen; Adaptive Engine/Ben controls remain visible.
- Controlled manual AppContainer retained; no Hilt/Dagger migration and no Compose migration.
- Ben/AppManagers/StudyCore/ImportPipelineCoordinator ownership was not duplicated.
- BenModelLab now uses the application container for model artifact storage and lifecycleScope/Activity Result API.

## Stage 7 — instrumented/device tests
Existing and expanded Android tests cover:
1. AppContainer wiring and authoritative AppManagers.
2. SQLite schema/database smoke path.
3. ImportPipelineCoordinator success/serialization path.
4. Imported-question -> QuizActivity launch path.
5. Quiz notes save/append/read path.
6. MainActivity startup.
7. SettingsActivity startup and Adaptive Engine/Ben control visibility.
8. Adaptive Engine configuration recreation.
9. HtmlImportActivity empty-input startup.
10. NotesActivity startup.
11. FlashcardActivity startup.
12. Additional crash-sensitive Activity startup paths.
13. Ben deterministic fallback when neural backend is unavailable.
14. EmbeddingGemma bounded deterministic fallback.
15. Provisioned EmbeddingGemma real inference when model + tokenizer are installed.
16. Backend telemetry must report QNN or CPU/XNNPACK execution for real inference.
17. Ben cognitive-control safe defaults.

CI already executes `testDebugUnitTest` and `connectedDebugAndroidTest` on an Android emulator. Actual execution remains a CI/device gate; local Android compilation is unavailable in the current environment because `services.gradle.org` cannot be resolved.

## Stage 8 — architecture enforcement
Added `tools/architecture_regression_audit.py` plus `tools/architecture_baseline.json`.

The CI gate now prevents:
- excessive Activity growth (>10% and >80 lines above accepted baseline; absolute >1800 lines also fails)
- new direct QBankDb/ProgressStore/SharedPreferences access from Activities
- new Activity-side Manager/Engine construction beyond accepted baseline
- `runBlocking`, `GlobalScope`, `Thread.sleep`, and fixed-rate scheduler patterns in Activities
- growth of process-wide `object` singleton declarations without an explicit baseline update
- loss of the AppContainer boundary for MainActivity, QuizActivity, SettingsActivity, or HtmlImportActivity
- accidental Activity-side construction/ownership of AppManagers children

The existing deeper XML, duplicate-ID, catastrophic-catch, startup/lifecycle, dependency, zstd, QNN, SentencePiece, signing, and runtime-risk audits remain enabled.

## Adaptive Engine UI redesign
Added `AdaptiveEngineDashboardView`.

The Adaptive Engine page now has a compact live control-room visualization with:
- live engine topology graph
- Adaptive/Ben/Runtime/Resilience/Battery/RAG nodes
- animated central pulse
- live histogram of learning/confidence/health/neural/evidence/RAM signals
- live stage + RAM + thermal status

The visualization is presentation-only. It does not own policy or poll engines independently. SettingsScreen feeds it lifecycle-bound snapshots, so it stops updating when Settings is not STARTED.

## Launcher artwork
The user-supplied copper/red Rovex emblem was converted into a transparent adaptive foreground and monochrome mask, with legacy density launcher assets regenerated. White background/noise was removed so the emblem remains clean over the adaptive icon background.

## Static deep scan
- XML parse: PASS
- duplicate IDs per individual XML layout: PASS
- broad `catch(Throwable)`: 0
- `runBlocking` main source: 0
- `GlobalScope` main source: 0
- `Thread.sleep` main source: 0
- unsafe SQLite PRAGMA pattern: PASS
- target Activities direct persistence ownership: PASS
- Stage 8 architecture regression script: PASS
- changed Kotlin structural scan: no structural compiler diagnostics found; Android/AndroidX unresolved references are expected without the Android classpath.
- ZIP integrity: PASS after packaging

## Build truth
Local Gradle compilation was attempted with `--offline` and still attempted to resolve Gradle 9.3.1 from `services.gradle.org`; DNS resolution is unavailable in this environment. Therefore no local Android compile is claimed.

**CI status: PENDING. Do not call v8.3.121 green until GitHub CI and connectedDebugAndroidTest actually pass.**
