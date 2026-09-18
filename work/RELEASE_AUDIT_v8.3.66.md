# Rovex v8.3.66 Stability Audit

## Release identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.66`
- versionCode: `164`
- Baseline: v8.3.62 source with bundled stability/measurement hardening.

## Changes
1. Added bounded/normalized Ben research input policy (4096 characters) to keep the local research gateway allocation-bounded.
2. Added unit coverage for Ben input normalization and size limits.
3. Expanded Macrobenchmark cold-start metrics with `FrameTimingMetric` and `MemoryCountersMetric`, while retaining startup timing, peak memory, and Rovex trace sections.
4. Added CI guards for the new Ben policy and benchmark metrics and synchronized release identity.

## Verification
- XML parsing: PASS
- stale v8.3.62/versionCode 160 references in active source/workflow: PASS (historical audit documents intentionally retain history)
- scheduler-label regression scan: PASS
- raw profileableByShell manifest regression scan: PASS
- JankStats variant isolation: PASS
- BenResearchInputPolicy standalone Kotlin compilation: PASS
- benchmark source metric presence: PASS
- ZIP integrity: PASS

## Limitations
Full Android/AGP compilation remains CI-authoritative because the current environment cannot reliably resolve the Gradle distribution from `services.gradle.org`.
