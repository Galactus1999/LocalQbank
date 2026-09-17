# Rovex v8.3.78 — Ben QBank Knowledge Learning

Version code: 176
Package: com.localqbank.library

## Ben improvements
- Added a resumable WorkManager-backed QBank knowledge-learning pipeline.
- Ben can now process imported QBank questions in bounded background batches and extract concepts/domains into his persistent local knowledge graph.
- Added keyset-paginated QBank access so the complete corpus is never materialized in memory for AI learning.
- Added batch transactional knowledge-index ingestion for substantially lower SQLite overhead than one-question-at-a-time writes.
- Added Adaptive Engine controls to start/resume or rebuild Ben's QBank knowledge.
- Added persistent build state/progress telemetry visible from Adaptive Engine.
- Preserved the existing deterministic RAG path, learner model, specialist routing, verifier and neural safety governor.

## Architecture / safety
- QBank source data is read-only; only Ben's separate knowledge database is mutated.
- Learning is offline-first and does not train neural weights.
- WorkManager keeps indexing off the foreground UI path.
- Keyset pagination and bounded text windows cap memory usage.
- Cancellation is cooperative and leaves a valid partial index for later resume.
- No new third-party AI/model dependency.
- No neural model is bundled.
- AppManagers ownership remains unchanged.
