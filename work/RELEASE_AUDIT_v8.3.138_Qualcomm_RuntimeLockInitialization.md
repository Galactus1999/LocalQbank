# Release Audit — v8.3.138

- Version: 8.3.138 / versionCode 236
- Base: v8.3.135
- Primary defect addressed: Qualcomm AOT runtime libraries absent from `nativeLibraryDir` at device preflight.
- Architecture: deterministic Ben/RAG remains authoritative; neural path is optional and fail-closed.
- Runtime correction: verified APK-owned arm64 runtime extraction fallback for base/split APK layouts.
- Runtime directory is app-private and used only after exact required-library validation.
- Runtime never downloads binaries.
- CI checks full Qualcomm runtime library set in debug/release/profile APKs.
- Existing signing/zstd/update invariants retained.

## Verification status

Static source changes can be audited locally. Android Gradle compilation is **not claimed green** unless CI passes. Qualcomm NPU execution is **not claimed working** until a v8.3.138 device diagnostic demonstrates runtimeGate=PASS and successful inference.


## v8.3.138 correction
- Fixed Qualcomm runtime extraction lock initialization order in `BenEmbeddingGemmaEngine.Runtime`.
- The synchronization monitor is now initialized before `init{}` executes, preventing the observed `NullPointerException: Attempt to do a synchronize operation on a null object`.
- No model, tokenizer, tensor-contract, or Qualcomm execution semantics were changed.
