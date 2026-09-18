# Rovex v8.3.84 — Release Audit

## Architecture
- EmbeddingGemma is an accelerator for semantic retrieval, not Ben's source of truth.
- Canonical corpus remains QBankDb/FTS4.
- Grounded neural pipeline can rerank a bounded candidate set; if the embedding runtime is unavailable or denied, deterministic retrieval continues.
- Model lifecycle is short-lived and CPU-first; inference is off the UI thread.

## Model handling
- No model weights are bundled.
- User-selected `.tflite` artifact is copied with bounded streaming into app-private storage.
- TFLite magic header is validated before activation.
- EmbeddingGemma is gated under Gemma terms; Rovex does not auto-download it.

## Build gate
- Android Gradle compilation is not claimed locally unless the real Gradle build completes. CI remains authoritative.
- CI checks versionCode 182/versionName 8.3.84, package ID, signing certificate, ARM64 zstd JNI, neural source files, and absence of bundled model binaries.

## Research basis
Google's current Android Text Embedder documentation explicitly supports EmbeddingGemma 300M, documents task-specific formatting, and reports approximately 200 ms CPU latency on a Samsung S26 Ultra benchmark. LiteRT's current Android documentation also lists an EmbeddingGemma semantic-similarity demo.
