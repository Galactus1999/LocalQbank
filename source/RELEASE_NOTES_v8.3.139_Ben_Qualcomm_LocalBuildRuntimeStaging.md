# Rovex 8.3.139 — Ben Qualcomm Local-Build Runtime Staging

VersionCode: 237

## Root cause of the v8.3.138 device diagnostic

The device diagnostic reported:

```
Failure: EmbeddingGemma runtime: java.lang.IllegalStateException: Bundled Qualcomm
runtime library not found in installed APKs: libLiteRtDispatch_Qualcomm.so
```

This is not a regression in `BenEmbeddingGemmaEngine`. The engine is correctly
refusing to execute a Qualcomm DISPATCH_OP graph without its matching vendor
runtime — that fail-closed behavior is intended.

The real gap: `tools/prepare_litert_qualcomm_v75_jit.sh` (which downloads the
official LiteRT Qualcomm v75 JIT runtime and stages it into
`app/src/main/jniLibs/arm64-v8a`) was only ever invoked as a separate step in
`.github/workflows/android-build.yml`. Any build that didn't go through that
exact CI job — a local `./gradlew assembleDebug`, an Android Studio "Run", a
`profile` build installed straight from a dev machine — never ran the script.
`jniLibs/arm64-v8a` stayed empty, the resulting APK's `lib/arm64-v8a/` never
contained `libLiteRtDispatch_Qualcomm.so` or the QNN libraries, and the
runtime-extraction fallback introduced in v8.3.136 correctly reported them
missing at first NPU preflight.

## Fix

`app/build.gradle.kts` now registers a `prepareQualcommRuntime` task that runs
`tools/prepare_litert_qualcomm_v75_jit.sh` and wires it as a dependency of
`preBuild`. Every variant's build — local or CI — depends on `preBuild`, so the
Qualcomm runtime is guaranteed to be staged before any APK is assembled.

- `onlyIf` skips the task (and the network fetch) once all required libraries,
  including at least one `libQnnHtpV75*` stub/skel, are already present and
  non-empty in `jniLibs/arm64-v8a`, so the local edit-build-install loop stays
  fast after the first run.
- The CI workflow's standalone "Prepare LiteRT Qualcomm SM8650/v75 JIT
  runtime" step is left in place; it now simply pre-warms the same directory
  the Gradle task checks, so CI behavior is unchanged.
- No change to the EmbeddingGemma tensor contract, Qualcomm AOT dispatch
  strategy, deterministic Ben/RAG fallback, or model artifacts.

## Scope

`app/build.gradle.kts` and `.github/workflows/android-build.yml` (version
assertions updated to 237 / 8.3.139) only.

## Verification status

Static source changes can be audited locally. Because `tools/prepare_litert_qualcomm_v75_jit.sh`
downloads a proprietary Qualcomm archive over the network, this fix could not
be exercised end-to-end in this environment (no network access here). Android
Gradle compilation is **not claimed green** and Qualcomm NPU execution is
**not claimed working** until a v8.3.139 build — produced via a normal
`./gradlew assembleDebug`/`assembleRelease` with network access, no manual
script step — installs and a device diagnostic shows `runtimeGate=PASS` with
successful inference.
