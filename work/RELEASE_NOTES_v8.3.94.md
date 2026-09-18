# Rovex v8.3.94

- VersionCode: 192.
- CI compatibility correction: restore the resolvable MediaPipe tasks-text 0.10.35 artifact.
- EmbeddingGemma TextFormatContext execution is fail-closed in this build because the required newer Android API is not published as a stable Maven artifact resolvable by CI.
- The user-imported EmbeddingGemma model remains stored and available for a future compatible backend; no model is bundled.
- Deterministic QBank/RAG retrieval remains authoritative and active.
- No new AI feature added.
