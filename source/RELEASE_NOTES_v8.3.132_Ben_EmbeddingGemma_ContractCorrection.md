# Rovex v8.3.132 — Ben EmbeddingGemma Contract Correction

## Root cause
The v8.3.130/v8.3.131 EmbeddingGemma runtime incorrectly depended on LiteRT exposing the original `input_ids` tensor name. The installed Qualcomm AOT/dispatch graph successfully exposed a positional input buffer, but the Kotlin name lookup did not expose that tensor name. The diagnostic therefore aborted before inference.

## Correction
- Switched the EmbeddingGemma execution boundary to LiteRT's signature-ordered `createInputBuffers()` contract.
- Supports both one-buffer Qualcomm AOT graphs and the standard two-buffer `input_ids + attention_mask` graph.
- Detects INT32/INT64 integer input buffers through public TensorBuffer operations.
- Infers actual sequence length from the installed model buffer instead of forcing 512.
- Reports actual input-buffer count/size in the diagnostic.
- Searches output buffers for the 768-value FLOAT32 embedding instead of blindly assuming output #0.
- Retains NPU-only handling for Qualcomm DISPATCH_OP artifacts and deterministic fallback.
- Retains native-resource cleanup on initialization failure.

## Important model finding
LiteRT Community currently publishes Qualcomm EmbeddingGemma variants for fixed sequence lengths 256, 512, 1024 and 2048. The next diagnostic will report the actual sequence length of the installed artifact. We will not replace the user's model until that runtime evidence proves the artifact is unsuitable.

## Validation status
Android CI/device validation is still required. No claim of CI-green or successful NPU inference is made by this source release alone.
