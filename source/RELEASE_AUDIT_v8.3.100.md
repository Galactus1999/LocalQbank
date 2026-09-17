# Rovex v8.3.100 Audit

- Baseline: v8.3.96, not the incorrect v8.3.99 raw-tensor experiment.
- Official Google model-card cross-check: Android RAG deployment uses the EmbeddingGemma LiteRT model plus `sentencepiece.model`.
- No raw tensor-name/shape guessing.
- No Hugging Face `tokenizer.json` requirement.
- Inference remains off UI thread and bounded to 10 seconds.
- Deterministic retrieval remains authoritative fallback.
- Runtime dimension mismatch is detected before success is recorded.
- Settings exposes the actual telemetry error.
- Local Android Gradle compilation is not claimed; CI remains the build gate.
