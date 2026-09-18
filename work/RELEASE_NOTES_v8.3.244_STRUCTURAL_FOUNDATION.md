# Rovex v8.3.244 — Structural Foundation / Stability Architecture

Version code: 338
Application ID: `com.localqbank.library`

## Purpose
This release begins the structural-debt phase. It intentionally prioritizes observability, boundaries, shared AI context contracts and incremental modern UI architecture instead of adding a large new feature surface.

## Implemented
- Central privacy-bounded `RovexObservability` seam with bounded Crashlytics breadcrumbs, screen/stage state and non-fatal diagnostics.
- Application lifecycle breadcrumbs and splash/startup health instrumentation.
- Shared `ContextPack` contract for question/exam-profile/options/selected-answer/QBank explanation context.
- Shared `EvidencePack` contract with explicit QUESTION/ANSWER/DISTRACTOR separation; only answer evidence is citation-authoritative.
- `ContextPackBuilder` used by Quiz AI context and grounded neural pipeline so context construction cannot silently diverge.
- Quiz repository boundary strengthened with narrow question/note/collection data-source interfaces and compatibility aliases under the new `data.quiz` package.
- First controlled package migration: `ai.context`, `core.observability`, `data.quiz`, `learning`, `exam`, `ui.ai`.
- Minimal Compose-first Ben AI control surface embedded into the existing View-based Frankenstein screen using official Compose/View interoperability.
- Pure domain contracts for Learning Observatory and Mock Examination subsystems; no duplicate manager/business owner introduced.
- New unit tests for ContextPack/EvidencePack and mock-exam policy invariants.
- Architecture regression gate extended to cover the new package boundaries and unified AI contracts.

## Preserved
- Application ID and persistent signing certificate.
- AppManagers as authoritative manager owner.
- Deterministic QBank/Graph-RAG as authoritative source of truth.
- Existing isolated Ben inference process and cancellation/release invariants.
- Existing APKG/zstd, Firebase, Google Sign-In, SRS, backup and flashcard architecture.

## Verification
- XML/resource/static audit: PASS.
- Architecture regression audit: PASS.
- Android Gradle compile: must be verified by GitHub Actions; local environment cannot resolve the configured Gradle distribution from `downloads.gradle.org`.
- Release is not considered green until CI and device validation pass.
