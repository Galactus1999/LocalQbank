# Rovex v8.3.83 Audit

Baseline: v8.3.82.

Changes audited:
- BenGroundedNeuralPipeline.kt added.
- Adaptive Engine adds a grounded neural pipeline test.
- versionCode 181 / versionName 8.3.83.
- CI expectations updated.

Design invariants:
- Neural output is never the source of truth.
- Local QBank evidence is bounded before prompt construction.
- Existing Ben deterministic verifier remains in the output path.
- Neural failure falls back to deterministic Ben.
- No scheduling/business ownership added.
