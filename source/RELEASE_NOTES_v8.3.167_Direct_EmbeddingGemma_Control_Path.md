# Rovex 8.3.167 — Direct EmbeddingGemma Control Diagnostic

## Purpose
Restore the previously working v8.3.140-style direct EmbeddingGemma diagnostic as the authoritative model/runtime control experiment. The isolated Ben IPC diagnostic remains available as a separate test rather than being allowed to hide model-runtime results behind Binder timeouts.

## Changes
- `RUN FULL EMBEDDINGGEMMA TEST` now calls `BenEmbeddingGemmaEngine.diagnostic()` directly from the main app process on `Dispatchers.IO`.
- Preserves engine-native phase progress: artifact check, hashing, graph inspection, tokenizer parsing, Qualcomm runtime preparation, NPU provider check, CompiledModel creation, tensor inspection, semantic smoke test and completion.
- Adds a 150-second direct diagnostic deadline.
- Adds `RUN ISOLATED BEN IPC TEST` as a separate engineering diagnostic.
- Direct and IPC failures are persisted separately with explicit reason strings.
- No study/question content is stored by diagnostics.
- Version 8.3.167 / versionCode 264.

## Validation requirement
CI and real-device validation are required. Do not treat this source package as CI-green until the actual Android CI build passes.
