# Rovex v8.3.134 — Qualcomm AOT Dispatch-Only Correction Audit

## Baseline
- v8.3.133 / versionCode 231
- applicationId `com.localqbank.library`
- LiteRT `2.2.0`
- Target device: Qualcomm SM8650 / Hexagon V75

## Evidence driving this release
v8.3.133 device diagnostics established:
- Qualcomm SM8650 AOT EmbeddingGemma Seq512 artifact is correctly recognized.
- Tensor contract is discovered: `[1,512]` INT32 input, one compiled output buffer.
- `CompiledModel.create()` succeeds.
- `Environment.getAvailableAccelerators()` reports NPU/GPU/CPU.
- Actual `CompiledModel.run()` fails immediately with generic `LiteRtException: Failed to invoke the compiled model`.
- Generic portable Seq512 EmbeddingGemma works on CPU, proving tokenizer/application tensor contract.

## v8.3.134 changes
1. Qualcomm AOT execution now uses an explicit **DispatchLibraryDir-only** LiteRT Environment.
   - Avoids exposing `CompilerPluginLibraryDir` for an already AOT-compiled DISPATCH_OP artifact.
   - The compiler plugin remains available in the build for compatibility, but is no longer supplied through the EmbeddingGemma AOT Environment.
2. Qualcomm options are explicitly supplied:
   - Log level: VERBOSE
   - HTP performance mode: BURST
   These are diagnostic/runtime execution options only; they do not become Ben scheduling policy.
3. Qualcomm runtime inventory is included in the diagnostic backend details:
   - `libLiteRtDispatch_Qualcomm.so`
   - `libQnnSystem.so`
   - `libQnnHtp.so`
   - `libQnnHtpV75Stub.so`
   - `libQnnHtpV75Skel.so`
   Each present library reports byte size and SHA-256.
4. `ADSP_LIBRARY_PATH` remains explicitly set to the app native-library directory.
5. The AOT path remains fail-closed: no CPU execution of a Qualcomm DISPATCH_OP artifact.

## Static audit
- XML parse: PASS — 26 XML resources
- Duplicate IDs within individual layouts: PASS
- Architecture cohesion: PASS
- Single intent-routing owner: PASS
- Orchestration/control contracts: PASS
- No undocumented orchestrator/coordinator classes: PASS
- Legacy `org.tensorflow.lite.Interpreter` references in active Kotlin source: PASS / none
- Stale v8.3.133 release identity in active source/workflow: PASS / none
- ZIP creation/integrity: PASS

## Warnings retained
- `QuizActivity.kt`: large source file (~1646 lines)
- `BenEmbeddingGemmaEngine.kt`: large source file (~905 lines)
These were not blindly split during a runtime-focused correction.

## Android compilation
Local Gradle compilation was attempted but could not start because this environment cannot resolve `downloads.gradle.org` while downloading Gradle 9.3.1. Therefore Android compilation is **UNVERIFIED**. CI remains the authoritative build gate.

## Research basis
Google LiteRT's Qualcomm HTP instructions distinguish AOT execution from JIT and show AOT execution using the dispatch library plus Qualcomm QNN runtime libraries and `ADSP_LIBRARY_PATH`. LiteRT v2.2.0's Kotlin `Environment.create(context, provider, ...)` automatically supplies both dispatch and compiler-plugin directories; v8.3.134 intentionally uses only `DispatchLibraryDir` for this precompiled AOT artifact. Google also documents Qualcomm `QualcommOptions`, including log level and HTP performance mode.

## Release conclusion
v8.3.134 is a **targeted runtime-contract correction/diagnostic build**, not yet a proven NPU-green release. The decisive device test is whether the SM8650 AOT model can now invoke through a dispatch-only Environment and, if not, whether the new runtime-library inventory plus verbose Qualcomm logs identifies the remaining binary/context mismatch.
