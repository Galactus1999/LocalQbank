# Rovex v8.3.91 Release Audit

- Baseline: v8.3.90 CI-green source.
- Change scope: TFLite artifact validation only; no new AI feature added.
- Fixed TFLite FlatBuffer identifier validation: checks `TFL3` at byte offset 4, not byte 0.
- Version: 8.3.91 / versionCode 189.
- Existing neural governor, model registry, LiteRT-LM, EmbeddingGemma engine, visual retrieval and fallback architecture retained.
- Android Gradle compilation must be verified by CI; no local Gradle-green claim is made here.
