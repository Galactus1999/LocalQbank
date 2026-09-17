# Rovex v8.3.76 — Ben Frankenstein Cognitive Expansion Audit

Baseline: v8.3.74 corrected Ben Frankenstein Adaptive Control compile-fix source.
Application ID: `com.localqbank.library`
Version code: `173`
Version name: `8.3.76`

## Changes
- Added `BenFrankensteinEngine` as a lightweight deterministic orchestration layer.
- Added bounded local RAG/QBank retrieval using existing Ren/ClinicalKnowledgeLayer paths.
- Added specialist routing for management, diagnosis, mechanism, pathology, imaging, genetics, complications/safety, epidemiology and general retrieval.
- Added bounded persistent experience telemetry (observations, verification rate, last specialist/concepts).
- Added Adaptive Engine controls for planner/reasoner, local RAG retrieval, specialist routing and experience memory.
- Added live RAM/thermal/power-save status and model failure-circuit status to the Ben control room.
- Added a three-consecutive-failure neural backend circuit breaker; repeated backend failures deny further model execution until reset.
- Neural inference remains optional and behind `BenInferenceBackend` + resource governor; no model dependency was added in this release.

## Safety / ownership
- Ben remains offline-first and model-independent.
- No scheduler, wake lock, system-setting or foreground-study ownership was added.
- AppManagers remains authoritative for manager/business domains.
- Neural inference is not initialized during app startup.

## Static audits performed locally
- Source ZIP SHA-256 matched the v8.3.74 baseline before modification: `65bbc3a9c625c0e823b6eea65f5db1282d23e23a607c9874893e033b51c506df`.
- XML parse audit: PASS.
- Duplicate IDs: PASS; duplicates are checked per individual XML layout file.
- Obvious `findViewById<T>()` cast audit: PASS.
- Broad `catch(Throwable)` audit: PASS (none found).
- Startup path inspected: Ben construction remains lightweight/lazy; no neural model load added.
- Package/version identity updated to `com.localqbank.library`, `8.3.76`, `173`.
- CI signing certificate gate retained.
- ARM64 zstd JNI gate retained.
- Macrobenchmark/JankStats/lifecycle infrastructure retained.
- CI workflow updated for the new version and added whole-project XML/duplicate-ID/catastrophic-catch and Ben wiring audits.

## Compilation status
Local Android/Gradle compilation is **UNVERIFIED** because the environment cannot resolve `services.gradle.org` to download Gradle 9.3.1. Pure Kotlin compilation of the unchanged `BenAiRuntimeGate.kt` passed. Full Android compilation/tests must be verified by CI.

## Research note
Current LiteRT-LM documentation supports a Kotlin Android API and explicitly recommends background initialization because `Engine.initialize()` can take significant time; model loading therefore remains deliberately out of this release until a benchmarked backend is justified. The current LiteRT-LM project also supports tool use and multimodality, making it a future accelerator candidate rather than a reason to make Ben dependent on a large model.
