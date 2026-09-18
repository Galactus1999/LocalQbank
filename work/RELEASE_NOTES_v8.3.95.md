# Rovex v8.3.96

- Replaces the disabled EmbeddingGemma compatibility hold with a CPU-first Google AI Edge LocalAgents RAG embedding backend.
- Uses the user-imported generic 512-token EmbeddingGemma `.tflite` plus its official `sentencepiece.model` tokenizer.
- Keeps execution behind the existing Ben policy/resource governor and deterministic fallback.
- Adds explicit tokenizer import/status in Adaptive Engine / Model Lab.
- No model weights are bundled.
- No background embedding indexing is introduced.
