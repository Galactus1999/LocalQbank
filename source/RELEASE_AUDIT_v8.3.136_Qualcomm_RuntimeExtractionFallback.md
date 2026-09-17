# Release Audit — v8.3.136

- Version: 8.3.136 / versionCode 234
- Base: v8.3.135
- Primary defect addressed: Qualcomm AOT runtime libraries absent from `nativeLibraryDir` at device preflight.
- Architecture: deterministic Ben/RAG remains authoritative; neural path is optional and fail-closed.
- Runtime correction: verified APK-owned arm64 runtime extraction fallback for base/split APK layouts.
- Runtime directory is app-private and used only after exact required-library validation.
- Runtime never downloads binaries.
- CI checks full Qualcomm runtime library set in debug/release/profile APKs.
- Existing signing/zstd/update invariants retained.

## Verification status

Static source changes can be audited locally. Android Gradle compilation is **not claimed green** unless CI passes. Qualcomm NPU execution is **not claimed working** until a v8.3.136 device diagnostic demonstrates runtimeGate=PASS and successful inference.
