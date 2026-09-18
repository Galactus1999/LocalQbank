# Rovex v8.3.93

## Ben EmbeddingGemma runtime API correction

- Bumped `com.google.mediapipe:tasks-text` from `0.10.35` to `0.10.36`.
- MediaPipe 0.10.36 is the release line that adds EmbeddingGemma TextEmbedder support and the `TextFormatContext` API used by this integration.
- Retained CPU-first, short-lived EmbeddingGemma execution and the hard Ben resource governor.
- Retained deterministic retrieval fallback on any neural initialization/inference failure.
- No model weights bundled in the APK.
- Version: 8.3.93 / versionCode 191.
