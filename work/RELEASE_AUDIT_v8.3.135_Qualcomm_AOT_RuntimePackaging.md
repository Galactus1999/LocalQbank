# Rovex v8.3.135 — Qualcomm AOT Runtime Packaging Correction Audit

## Purpose
Correct the v8.3.134 finding that the SM8650 EmbeddingGemma AOT model was being invoked while the APK's nativeLibraryDir did not contain the Qualcomm LiteRT/QNN runtime libraries required by the AOT dispatch path.

## Evidence from v8.3.134 device diagnostic
- Device: OnePlus CPH2691 / Qualcomm SM8650 / arm64-v8a
- Model: EmbeddingGemma 300M Qualcomm dispatch graph, Seq512
- CompiledModel.create(): PASS
- NPU exposed: PASS
- Invocation: FAIL
- Diagnostic explicitly reported these libraries missing from nativeLibraryDir:
  - libLiteRtDispatch_Qualcomm.so
  - libQnnSystem.so
  - libQnnHtp.so
  - libQnnHtpV75Stub.so
  - libQnnHtpV75Skel.so

## Corrections
1. Version bumped to 8.3.135 / versionCode 233.
2. CI already prepares the official LiteRT Qualcomm v75 runtime; CI now additionally verifies that the release, debug, and profile APKs actually contain the required arm64-v8a Qualcomm runtime libraries.
3. The EmbeddingGemma runtime now performs a hard pre-invocation runtime-library gate. If required AOT libraries are absent, Ben fails closed with an explicit diagnostic instead of attempting CompiledModel.run() and collapsing to a generic invocation failure.
4. AOT dispatch-only mode remains; the SM8650 model is not replaced with a CPU model.
5. Existing ADSP_LIBRARY_PATH handling and deterministic fallback are preserved.

## Static validation
- Architecture cohesion audit: PASS (existing warnings only for large QuizActivity and BenEmbeddingGemmaEngine)
- XML parse: PASS, 0 errors
- Duplicate IDs within individual layouts: PASS
- Changed Kotlin delimiter balance: PASS
- Version identity: PASS (8.3.135 / 233)
- CI runtime-library APK assertions: PASS (source-level checks present)

## Build status
Android Gradle compilation was not claimed green locally because the environment cannot resolve the configured Gradle distribution host. CI remains the authoritative build gate.

## Runtime expectation
On the next CI-built APK, the diagnostic must show the five required Qualcomm libraries as present. If they are present and invocation still fails, the problem is downstream of packaging and the next investigation should focus on Qualcomm AOT context/runtime compatibility rather than tensor discovery.
