# Rovex v8.3.129 — LiteRT / AGP 9 Namespace Fix

## CI failure corrected

The v8.3.128 CI build reached `:app:compileDebugKotlin` successfully but failed at `:app:processDebugMainManifest` because LiteRT 2.2.0 brings `com.google.ai.edge.litert:litert-api:2.2.0`, and AGP 9.1.1 rejects the duplicate `com.google.ai.edge.litert` namespace declared by both AARs.

## Correction

Added `android.uniquePackageNames=false` to `gradle.properties`. This is the upstream-documented temporary workaround for the LiteRT duplicate-namespace packaging defect. `litert-api` is intentionally NOT excluded because prior upstream reports show exclusion can cause runtime failures.

## Version

- versionName: 8.3.129
- versionCode: 227

## Verification

- Active build/workflow version assertions updated.
- LiteRT 2.2.0 retained.
- Qualcomm NPU runtime preparation retained.
- No Kotlin compilation failure was present in the supplied CI log; `compileDebugKotlin` completed with warnings before the manifest task failure.
- Full Android build must still pass in CI after this correction.
