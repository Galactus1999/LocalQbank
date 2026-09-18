# Rovex v8.3.128 — EmbeddingGemma NPU Runtime Fix

- Corrected the EmbeddingGemma runtime selection for Qualcomm SM8650 dispatch graphs.
- Upgraded LiteRT CompiledModel runtime to 2.2.0.
- Added Qualcomm NPU provider/environment initialization.
- Added fail-closed handling for missing NPU runtime libraries.
- Preserved CPU/XNNPACK for portable models.
- Removed invalid INT64 TensorBuffer read/write calls from the Android path.
- Added CI preparation for the official Qualcomm SM8650/v75 LiteRT JIT runtime.
- Deterministic Ben/RAG remains authoritative until the physical semantic contract test passes.
