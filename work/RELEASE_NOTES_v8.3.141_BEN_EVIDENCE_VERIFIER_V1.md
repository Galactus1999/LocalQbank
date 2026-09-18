# Rovex v8.3.141 — Ben Evidence-Grounded Verifier v1

VersionCode: 239  
Base: v8.3.140 / versionCode 238

## Purpose
Strengthen the boundary between Gemma 3 270M neural drafting and student-facing answers without adding another model or heavy NLP dependency.

## Implemented
- Replaced the rejected-neural-draft `repair()` path with fail-closed handling in the grounded neural pipeline.
- A neural draft that fails Ben's verifier is discarded and the pipeline calls deterministic `BenCognitiveArchitecture`/`RenCognitiveEngine` fallback.
- Grounded evidence now retains stable citation index and originating QBank hit id.
- Neural prompt explicitly requires `[1]..[6]` evidence citations and forbids invented citation ids.
- Citation ids are validated against the actual evidence supplied to the verifier.
- Conservative exam-critical numeric claim gate.
- Explicit high-confidence quantity conversions only:
  - days ↔ weeks
  - months ↔ days (30-day approximation, deliberately conservative)
  - years ↔ years
  - mg ↔ g
  - mcg/µg ↔ mg
  - mg/kg ↔ g/kg and mcg/kg/µg/kg
  - mL ↔ L
  - percentage ↔ a small 1/2, 1/4, 3/4 equivalence table
  - age expressed as `40 years` ↔ an evidence `age 40` literal
- Critical recommendation/criteria phrases require a nearby valid citation.
- Visual-finding language in visual-intent answers requires a nearby valid citation.
- No general contradiction classifier or semantic clinical equivalence engine was introduced.
- Adaptive Engine live telemetry now exposes critical-claim support counts, citation status and neural accept/reject state.
- Added JVM tests for supported/wrong critical values, invented citations, missing citations, explicit unit conversion, unsupported cutoffs, ordinary prose, and critical recommendation citation gating.

## Safety policy
A rejected neural medical draft is never repaired into a student-facing answer. The verifier is intentionally conservative: unknown semantic relationships fail closed.

## Build status
Android Gradle compilation was attempted but could not run because the Gradle distribution host was unavailable via DNS in the current environment. Therefore this release is **NOT claimed CI-green**.
