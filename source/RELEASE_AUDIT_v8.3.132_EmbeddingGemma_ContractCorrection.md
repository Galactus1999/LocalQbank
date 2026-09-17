# Rovex v8.3.132 — EmbeddingGemma Contract Correction Audit

## Root cause confirmed
The v8.3.130/v8.3.131 runtime boundary incorrectly required LiteRT to expose the original `input_ids` tensor name through `getInputTensorType(name)`. On the installed Qualcomm AOT/dispatch graph, LiteRT successfully created a positional bulk input buffer but did not expose that name through the Kotlin name lookup. The app therefore aborted before inference with `input_ids tensor is not exposed by LiteRT; bulk input buffers=1`.

## Correction
- Use `CompiledModel.createInputBuffers()` as the authoritative signature-ordered buffer contract.
- Support both exported forms:
  - 2 buffers: input_ids + attention_mask
  - 1 buffer: input_ids with masking compiled/internal to the AOT graph.
- Detect INT32 vs INT64 from the public TensorBuffer read API.
- Write token/mask data using the detected integer type.
- Infer sequence length from the actual buffer instead of hard-coding 512.
- Accept the supported EmbeddingGemma fixed-context variants rather than requiring exactly 512.
- Select the FLOAT32 output buffer that actually contains 768 values instead of blindly assuming output buffer #0.
- Preserve Qualcomm NPU-only handling for DISPATCH_OP graphs and deterministic fallback on failure.
- Preserve native cleanup on initialization failure.

## External verification
Official LiteRT Kotlin CompiledModel exposes positional `createInputBuffers()`, named buffer APIs, and map-based execution. The LiteRT source also shows that an NPU-only option internally includes CPU for partially compiled models. Official LiteRT Community currently publishes Qualcomm EmbeddingGemma variants for multiple fixed sequence lengths, including 256/512/1024/2048, so the runtime must inspect the installed graph rather than assume 512.

## Audit status
- Changed Kotlin source inspected: PASS
- Old `input_ids tensor is not exposed` failure removed: PASS
- Legacy TensorFlow Lite interpreter reference: PASS (none in app Kotlin sources)
- XML parse: PASS
- Duplicate IDs within individual layouts: PASS
- Version: 8.3.132 / versionCode 230
- Android compile: NOT CLAIMED GREEN; CI remains authoritative.
- Device inference: NOT CLAIMED PASS until the v8.3.132 APK is installed and a new diagnostic shows actual inference and semantic separation.
