# Rovex v8.3.131

## Architecture cohesion
- Made `RenIntelligenceOrchestrator` the single authoritative intent/capability router.
- Reduced `IntelligenceOrchestrator` to a compatibility facade using the same router instance.
- Added architecture-cohesion CI guard.
- Registered previously undocumented coordinator boundaries in `ManagerContractRegistry`.
- Added `RELEASE_INDEX.md` rather than deleting historical audit records.

## Stability principle
No speculative renaming or broad manager rewrite was performed. Large classes remain explicitly flagged for future, test-backed extraction.
