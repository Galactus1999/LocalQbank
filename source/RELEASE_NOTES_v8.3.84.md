# Rovex v8.3.84 — Ben EmbeddingGemma Semantic Retrieval

- Added an optional CPU-first `BenEmbeddingGemmaEngine` using MediaPipe Tasks Text 0.10.35 and the documented Text Embedder API.
- EmbeddingGemma 300M remains user-imported and is never bundled or downloaded by Rovex.
- Added bounded semantic reranking of the top local QBank candidates inside the grounded Ben neural pipeline. Deterministic FTS/concept retrieval remains the fallback.
- Added an Adaptive Engine/Ben control-room test for EmbeddingGemma semantic similarity.
- Embedding model artifacts are validated as LiteRT/TFLite files and stored app-private.
- CPU-only, resource-governed, short-lived embedder lifecycle; no model is initialized during app startup.
- Increased the conservative EmbeddingGemma artifact estimate to 320 MB / 520 MB runtime to accommodate current mobile LiteRT variants.
- VersionCode 182 / versionName 8.3.84.
