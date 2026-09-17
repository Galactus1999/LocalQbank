# Rovex v8.3.73 — Ben Cognitive Architecture Audit

## Change scope
- Added model-independent Ben cognitive core: intent routing, clinical concept graph, learner memory, planning, tool registry, and answer verification.
- Integrated BenLocalResearchEngine with the cognitive core while preserving Ren/QBank fallback.
- Added resource governor for optional local neural backends: user kill switch, model-size policy, available-RAM headroom, thermal status, power-save and foreground gating.
- No neural model dependency or model weights were added.
- Existing package ID and release signing architecture are unchanged.

## Verification
- XML parsing: PASS
- Per-layout duplicate ID scan: PASS
- findViewById/XML widget compatibility audit: PASS
- stale active version scan: PASS
- workflow YAML parse: PASS
- pure Kotlin Ben resource-gate compilation: PASS
- Ben resource-gate executable checks: PASS
- source structural/brace audit: PASS
- dangerous synchronous/blocking pattern scan: PASS
- Gradle Android unit-test execution: BLOCKED by environment DNS resolving services.gradle.org for Gradle 9.3.1; no compilation result claimed

## Safety architecture
The optional model remains an accelerator. Ben's local cognitive core, retrieval, learner memory and verification continue to operate without it. A model is denied before initialization when policy or runtime resources are unsafe.
