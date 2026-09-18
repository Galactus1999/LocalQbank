# Rovex Architecture Cohesion — v8.3.131

## Red-team finding
A filename-level review correctly identified a legitimate risk area: multiple classes had orchestration/control names. It did not establish that they were God classes because the supplied artifact did not expose readable source logic.

## Correction
`RenIntelligenceOrchestrator` is the single authoritative intent/capability routing owner. `IntelligenceOrchestrator` remains only as a compatibility facade and delegates routing to the same BIO instance. It no longer duplicates wrong-question filtering, subject matching, complexity detection, or capability routing.

The Ben/Rovex prefixes were intentionally retained. They identify subsystem ownership; renaming them solely for aesthetics would increase churn without improving cohesion.

## Ownership preserved
AppManagers, PerformanceManager, AdaptiveEngineManager, EngineHealthManager, ResilienceManager, EngineMeshCoordinator, StudyCore and ImportPipelineCoordinator remain authoritative. No parallel database/progress/business-logic owner was introduced.

## Release hygiene
Historical audit/release-note files remain intact for traceability. `RELEASE_INDEX.md` provides a single navigation point instead of deleting historical evidence.

## Automated guard
`tools/audit_architecture.py` checks the single-router invariant, compatibility-facade boundary, contract registration, and accidental proliferation of undocumented orchestrators/coordinators. Large classes are warnings rather than automatic rewrites.
