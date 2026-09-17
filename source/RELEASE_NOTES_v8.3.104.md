# Rovex v8.3.104 — Ben EmbeddingGemma Qualcomm/NPU correction

- Corrected the EmbeddingGemma failure-circuit behavior that could permanently block manual diagnostics after earlier backend failures. The circuit is reset for this runtime epoch and explicit manual tests clear only the backend failure counter.
- Added precise governor block diagnostics instead of the generic “blocked by policy/resource governor” message.
- Added Qualcomm QNN HTP execution for Snapdragon Qualcomm devices, using the official QNN LiteRT delegate path; CPU/XNNPACK remains the deterministic fallback.
- Added QNN delegate initialization fallback: a QNN/delegate/model incompatibility must not crash or disable deterministic Ben.
- Broadened token input compatibility to INT64 or INT32 while retaining the required [1,512] sequence contract and 768-value float output contract.
- Exposed the active EmbeddingGemma backend in Ben neural telemetry.
- Preserved the existing SentencePiece tokenizer, task prefixes, deterministic retrieval, Gemma 3 270M accelerator, AppManagers ownership, and AI resource governor boundary.
- Version: 8.3.104 / versionCode 202.

Build status: local Gradle compilation was attempted but Gradle distribution download was blocked by unavailable DNS for services.gradle.org. CI remains the authoritative Android build gate.
