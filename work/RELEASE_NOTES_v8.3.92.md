# Rovex v8.3.92

## Ben EmbeddingGemma runtime correction

- Version: 8.3.92 / versionCode 190.
- Reworked EmbeddingGemma execution to follow the current official MediaPipe Text Embedder API.
- Uses `TextEmbedder.createFromFile(...)` for the user-installed external TFLite artifact instead of custom memory mapping.
- Uses `TextEmbedder.TextFormatContext` with `RETRIEVAL_QUERY` + `RETRIEVAL_DOCUMENT` for semantic retrieval.
- Uses `SEMANTIC_SIMILARITY` formatting for Model Lab comparison.
- Removed manual EmbeddingGemma prompt prefix construction from Rovex; MediaPipe now performs task-specific formatting.
- Runtime failures now retain the exception class and bounded message in Ben telemetry for diagnostics.
- CPU-first execution and the existing AI resource governor remain unchanged.
- No model weights are bundled in the APK.

## Stability rule

This release changes only the EmbeddingGemma adapter and release identity/CI assertions. No new business-logic owner or background scheduler is introduced. Deterministic retrieval remains the fallback when neural execution is unavailable.
