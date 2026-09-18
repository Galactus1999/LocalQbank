# Rovex v8.3.133 — Release Audit

## Identity

- Package: `com.localqbank.library`
- Version: `8.3.133`
- versionCode: `231`
- Baseline: v8.3.132 EmbeddingGemma positional-contract correction

## Scope

Controlled correction for Qualcomm SM8650 EmbeddingGemma AOT invocation diagnostics. No model replacement, no CPU downgrade of the SM8650 AOT path, and no unrelated feature refactor.

## Research verification

- Google LiteRT v2.2.0 Kotlin `TensorBuffer` publicly exposes `writeLong()` / `readLong()`; the current code is therefore API-compatible with the pinned LiteRT 2.2.0 dependency.
- Google LiteRT v2.2.0 `Environment.create(context, NpuAcceleratorProvider, options, enableCompilerCache)` automatically supplies the provider's DispatchLibraryDir and CompilerPluginLibraryDir when the provider is supported and ready.
- Google LiteRT's Qualcomm documentation distinguishes host AOT, real JIT, and on-device AOT/cache flows.
- Google’s current EmbeddingGemma LiteRT Community repository publishes Qualcomm SM8650 Seq512 variants and reports NPU Seq512 benchmarks on its reference device.
- Google issue reports document real Qualcomm/SM8650 NPU failures with generic LiteRT exceptions and possible runtime/binary compatibility problems. This makes preserving low-level diagnostics essential rather than assuming the application tensor contract is wrong.

## Source changes audited

- `BenEmbeddingGemmaEngine.kt`
  - detailed exception-chain capture;
  - Logcat stack trace for Qualcomm invocation failures;
  - provider/runtime directory reporting;
  - `ADSP_LIBRARY_PATH` setup for Qualcomm Hexagon runtime;
  - environment accelerator reporting;
  - CompiledModel creation state reporting;
  - runtime/inference timing preservation;
  - failure reports retain Runtime-discovered contract information.
- `.github/workflows/android-build.yml`
  - v8.3.133 / versionCode 231 assertions;
  - canonical update artifact renamed;
  - stale restore-call assertion corrected.

## Static audit results

- Architecture cohesion audit: **PASS**
- Architecture regression audit: **PASS** — 16 Activities, 44 singleton declarations
- XML parsing: **PASS** — 26 XML resources
- Duplicate IDs within individual layouts: **PASS**
- `findViewById<T>()` vs XML widget audit: **PASS**
- Startup blocking-I/O audit: **PASS**
- Runtime-risk static audit: **PASS**
- Broad `catch(Throwable)` audit: **PASS**
- Legacy TensorFlow Lite Interpreter references: **PASS** (none)
- GeckoEmbeddingModel / localagents-rag legacy references: **PASS** (none)
- Stale v8.3.132 / versionCode 230 / old workflow identity: **PASS** (none in active source/workflow)
- Gradle workflow YAML parse: **PASS**
- Modified Kotlin lexical brace/parenthesis balance: **PASS**

## Architecture warnings

The existing architecture audit still warns about large files:

- `QuizActivity.kt`: ~1646 lines
- `BenEmbeddingGemmaEngine.kt`: ~862 lines

No blind split was performed in this release because the requested correction is runtime-diagnostic focused and stability is the priority.

## Build status

**Android Gradle compilation is NOT claimed green.** Local Gradle execution remains blocked when the wrapper cannot download the configured Gradle distribution because external DNS/network access is unavailable in the execution environment.

CI remains the authoritative Android build gate.

## Device validation status

v8.3.133 has not yet been device-validated. The next required device diagnostic is the Qualcomm SM8650 AOT model. Success criteria are:

1. Qualcomm provider supported/ready;
2. environment exposes NPU;
3. CompiledModel creation succeeds;
4. positional input contract is discovered;
5. `CompiledModel.run()` succeeds;
6. 768-dimensional finite embedding is produced;
7. semantic smoke test separates related from unrelated text.

If invocation still fails, the new report must be used to classify the failure as runtime/dispatcher/QNN/binary compatibility, model-context compatibility, or I/O contract mismatch before another implementation change is made.
