# Rovex v8.3.77 — Ben Cognitive Expansion III

Version code: 175
Package: com.localqbank.library

## Ben improvements
- Added a lazy persistent local knowledge index using the existing Rovex SQLite/QBank FTS corpus.
- Added a compact concept-edge graph that learns co-occurrence relationships from Ben interactions.
- Added bounded local evidence reranking without duplicating the QBank corpus in memory.
- Added Knowledge Index visibility and enable/disable control in Adaptive Engine.
- Added Knowledge Index statistics and a user-accessible graph reset.
- Integrated indexed evidence and learned graph connectivity into Ben planning confidence.

## Architecture / safety
- No new third-party dependency.
- No neural model added.
- Knowledge index opens lazily only when Ben uses it; it is not initialized from AppManagers startup.
- QBankDb remains canonical for question corpus/search; Ben does not mutate source questions.
- AppManagers ownership remains unchanged.
- Existing deterministic fallback and AI safety governor remain authoritative.
