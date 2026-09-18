# Rovex v8.3.93 Release Audit

- Baseline: v8.3.92 source.
- Version: 8.3.93 / versionCode 191.
- Primary fix: use the MediaPipe release line that actually contains EmbeddingGemma TextEmbedder support and `TextFormatContext`.
- `tasks-text` is pinned to `0.10.36`.
- EmbeddingGemma remains optional and CPU-first; deterministic retrieval remains the fallback.
- No neural model binaries are bundled.
- Full Android Gradle compilation is CI-gated; local compilation is not claimed unless the build actually completes.
