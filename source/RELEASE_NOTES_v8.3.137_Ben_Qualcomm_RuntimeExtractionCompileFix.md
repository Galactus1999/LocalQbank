# Rovex 8.3.137 — Ben Qualcomm Runtime Extraction Compile Fix

VersionCode: 235

## Fix
Corrected v8.3.136 Kotlin compilation errors in `BenEmbeddingGemmaEngine.Runtime` introduced by the Qualcomm runtime extraction fallback:
- runtime directory APIs now consistently use `File` rather than mixing `File` and `String`.
- `ADSP_LIBRARY_PATH`/LiteRT provider directory conversion occurs only at the OS/API boundary.
- removed invalid `companion object` declaration from the non-static inner `Runtime` class and replaced it with an instance lock.

## Scope
No change to the EmbeddingGemma tensor contract, Qualcomm AOT dispatch strategy, deterministic fallback, or model artifacts.

Android/CI compilation status: not claimed green until CI passes.
