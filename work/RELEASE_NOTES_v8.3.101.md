# Rovex v8.3.101

- VersionCode 199 / versionName 8.3.101.
- Replaced the experimental Gecko/LocalAgents EmbeddingGemma adapter with direct LiteRT Interpreter execution.
- Uses the official EmbeddingGemma SentencePiece companion tokenizer.
- Runtime introspects the actual model tensor contract instead of guessing tensor names/shapes.
- CPU/XNNPACK with 4 threads; off-UI-thread and governor controlled.
- Deterministic retrieval remains authoritative fallback.
