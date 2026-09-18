# Rovex v8.3.128 — EmbeddingGemma Qualcomm NPU Runtime Audit

## Baseline
- v8.3.127 / versionCode 225
- New version: v8.3.128 / versionCode 226

## Root cause from device diagnostic
The installed ~181 MiB EmbeddingGemma artifact reports `Qualcomm`, `SM8650`, and `DISPATCH_OP`. This is therefore a Qualcomm hardware-specific dispatch graph, not the portable CPU EmbeddingGemma artifact.

The previous v8.3.127 implementation incorrectly forced `Accelerator.CPU`, causing LiteRT CompiledModel initialization to fail before tensor inspection. `DISPATCH_OP` cannot be handled by CPU/XNNPACK.

## Correction
- LiteRT upgraded from 2.1.5 to current stable 2.2.0.
- Qualcomm dispatch graphs are detected from the model byte scan.
- Qualcomm graphs use `BuiltinNpuAcceleratorProvider` + `Environment` + `Accelerator.NPU`.
- NPU support is gated to Android API 31+.
- Missing/unsupported Qualcomm runtime fails closed with a structured diagnostic; no CPU attempt is made for a Qualcomm dispatch graph.
- Portable EmbeddingGemma graphs retain CPU/XNNPACK execution.
- Removed invalid `TensorBuffer.readLong()` / `writeLong()` usage; the current Android path explicitly requires INT32 token inputs, matching the known EmbeddingGemma Android contract used by this integration.
- Runtime/environment resources are closed safely when model compilation fails or the runtime closes.

## Build/runtime dependency
The official LiteRT Qualcomm JIT runtime libraries are not source-controlled. `tools/prepare_litert_qualcomm_v75_jit.sh` downloads the official LiteRT 2.2.0 JIT runtime archive, prepares Qualcomm QAIRT libraries, and copies only the SM8650/v75 arm64 runtime into `app/src/main/jniLibs/arm64-v8a` during CI/build preparation.

## Static checks
- Architecture regression audit: PASS
- XML parse: PASS (26 XML resources; 0 parse errors)
- Duplicate IDs: PASS (0 duplicate IDs within individual layout files)
- Legacy EmbeddingGemma Interpreter/QNN path: removed
- Invalid TensorBuffer long read/write calls: removed
- Stale v8.3.127/versionCode 225 active build references: corrected
- Kotlin parser-oriented compilation: no syntax/structural errors; standalone compiler lacks Android/Gradle dependency classpath, so this is not an Android compile claim.

## Build gate
Full Android Gradle/CI compilation and physical-device NPU inference remain unverified until CI executes the workflow and the resulting APK is installed on the SM8650 device.
