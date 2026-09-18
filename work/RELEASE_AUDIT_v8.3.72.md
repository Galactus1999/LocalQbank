# Rovex v8.3.72 Release Audit

## Scope
Ben local-model safety architecture and stability hardening.

## Changes
- Added `BenAiRuntimeGate`, a pure deterministic pre-load gate.
- Added persistent `BenAiRuntimePolicy` with a user-visible kill switch.
- Conservative model-start gate defaults to 1.2 GB estimated model size.
- Ben model backend is used only when explicitly enabled and the backend reports a safe non-zero model-size estimate.
- Extended `BenInferenceBackend` with model-size metadata and optional cleanup hook.
- Added Settings > Ben AI Safety controls.
- Preserved existing local Ren/QBank fallback.
- No LiteRT-LM dependency or model binary is bundled in this release.
- Bumped version to 8.3.72 / versionCode 170.

## Verification
- XML parsing: PASS
- Per-layout duplicate IDs: PASS
- Active release identity/stale-version scan: PASS
- Pure Kotlin Ben policy compilation: PASS
- Changed-source runtime pattern scan: PASS
- Full Android Gradle build: NOT LOCALLY VERIFIED (Gradle distribution DNS unavailable in environment)

## Online research basis
Current LiteRT-LM documentation requires background engine initialization and explicit engine cleanup. Current Android guidance recommends Macrobenchmark startup and frame metrics for regression detection. LiteRT-LM compatibility issues remain device/backend dependent, so this release keeps the runtime behind a guarded adapter boundary.
