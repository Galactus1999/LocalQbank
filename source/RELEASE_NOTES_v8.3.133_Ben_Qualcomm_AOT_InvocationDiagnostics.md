# Rovex v8.3.133 — Ben Qualcomm AOT Invocation Diagnostics

Version: **8.3.133**  
versionCode: **231**

## Purpose

This release is a controlled diagnostic correction for the installed EmbeddingGemma 300M Qualcomm SM8650 AOT artifact.

The v8.3.132 device test established two distinct paths:

- Qualcomm SM8650 AOT/`DISPATCH_OP` artifact: model initialization reached `CompiledModel`, but `CompiledModel.run(...)` failed with the generic `LiteRtException: Failed to invoke the compiled model`.
- Generic Seq512 CPU/XNNPACK artifact: full `[1,512] -> 768` EmbeddingGemma inference and semantic smoke test passed.

Therefore v8.3.133 does **not** replace or downgrade the user's SM8650 AOT model.

## Changes

### 1. Preserve the real Qualcomm invocation failure

`BenEmbeddingGemmaEngine` now captures the complete throwable cause chain (bounded to six levels) instead of reporting only the top-level LiteRT exception.

The diagnostic report now includes:

- backend runtime details;
- native/provider library directory;
- Qualcomm provider device support state;
- Qualcomm provider library readiness;
- LiteRT environment accelerator set;
- `CompiledModel.create(...)` success state;
- complete invocation failure cause chain.

The full exception is also written to Logcat under `BenEmbeddingGemma`.

### 2. Qualcomm Hexagon DSP library-path preparation

Before initializing the Qualcomm NPU provider, the process now exports the app's `nativeLibraryDir` as `ADSP_LIBRARY_PATH`.

This is a targeted Qualcomm/Hexagon runtime compatibility measure. It does not alter CPU models and does not introduce a CPU fallback for a hardware-specific AOT graph.

### 3. Preserve runtime state when inference fails

If `CompiledModel.run(...)` fails after the Runtime object has successfully initialized, the diagnostic report now preserves the discovered input/output contract, backend state, runtime initialization timing, and Qualcomm failure details instead of collapsing the report into a generic pre-initialization failure.

### 4. Inference timing

The Runtime records the last individual invocation duration so failed/successful runs can be distinguished more precisely.

### 5. LiteRT API compatibility check

The implementation continues to use LiteRT 2.2.0 `TensorBuffer.readLong()` / `writeLong()` support. The exact v2.2.0 Google source confirms these public Kotlin methods exist.

### 6. CI/release identity correction

Updated all v8.3.132 stale workflow assertions to:

- versionName `8.3.133`
- versionCode `231`
- canonical artifact `Rovex-UPDATE-v8.3.133`

Also corrected the stale `applyPendingFullRestore()` CI assertion so it counts the actual call site rather than the method declaration.

## Architecture policy

The SM8650 AOT model remains NPU-only. If Qualcomm execution fails, Ben must fail closed and use deterministic Ben/RAG rather than repeatedly retrying native inference or silently pretending that the AOT graph is a CPU model.

The generic CPU Seq512 model remains a diagnostic/control artifact and is not promoted as the final NPU solution.
