# Rovex v8.3.153 — Settings Control Center + Phase 2/3 Audit

## Release identity
- Version: 8.3.153
- versionCode: 250
- applicationId: com.localqbank.library

## UI work
- Reorganized the top-level Settings screen into a Control Center hierarchy:
  - Study & Learning
  - Appearance & UI
  - Intelligence & Automation
  - Data & Continuity
  - System & Performance
  - Advanced & Diagnostics
- Added live overview KPI cards from AdaptiveEngineManager telemetry.
- Reused AdaptiveEngineDashboardView for real health/confidence/latency/evidence visualization and a compact runtime data table.
- Kept detailed Adaptive Engine controls under the dedicated Adaptive Engine screen.
- No new heavyweight UI dependency introduced.

## Phase 3 work
- Added BenContrastiveEvidencePlanner.
- Separates QUESTION_EVIDENCE, ANSWER_EVIDENCE and bounded DISTRACTOR_EVIDENCE as retrieval metadata.
- Integrated the planner into BenGroundedNeuralPipeline prompt context.
- BenAnswerVerifier remains authoritative; contrastive roles do not become clinical truth.
- Added pure Kotlin tests for role separation and bounds.

## Static/preflight audit
- XML parse: PASS
- Duplicate IDs within individual layouts: PASS (0)
- findViewById/XML type audit: PASS (0 mismatches)
- runBlocking production source: 0
- GlobalScope production source: 0
- Thread.sleep production source: 0
- active catch(Throwable): 0
- architecture cohesion audit: PASS
- pure Kotlin compilation of new contrastive planner/evidence classes: PASS
- stale v8.3.152 / versionCode 249 CI release assertions: corrected

## Known warnings
- QuizActivity.kt remains large.
- BenEmbeddingGemmaEngine.kt remains large and is still scheduled for cohesive decomposition.
- Existing architecture regression baseline warning about singleton/object count remains pre-existing and is not suppressed.

## Android build status
Android/AGP compilation is NOT claimed green. Local Gradle cannot download Gradle 9.3.1 because downloads.gradle.org is DNS-inaccessible in this environment. CI remains the authoritative Android build gate.
