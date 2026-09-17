# Rovex 8.3.80 — Ben Neural Accelerator Foundation

- versionCode 178
- Added `BenNeuralModelRegistry` with vetted profiles for EmbeddingGemma 300M and a tiny Gemma 3 270M generation candidate.
- Added `BenNeuralModelManager` for bounded, app-private model artifact import/storage.
- Surfaced model readiness and install/remove controls in Adaptive Engine.
- Added CI checks preventing accidental model binaries from being bundled.
- No neural model is auto-downloaded, loaded during startup, or executed by this release.
- Existing `BenInferenceBackend` remains the sole inference execution boundary.
- Deterministic Ben remains fully functional when neural models are absent/off.
- Next integration gate is a separately verified LiteRT EmbeddingGemma backend after device/runtime compatibility validation.
