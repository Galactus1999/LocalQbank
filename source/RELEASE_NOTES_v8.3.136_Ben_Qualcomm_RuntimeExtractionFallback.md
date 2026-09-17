# Rovex v8.3.136 — Ben Qualcomm Runtime Extraction Fallback

## Why

v8.3.135 correctly added a Qualcomm runtime gate, but the device diagnostic still reported all five required runtime libraries missing from `ApplicationInfo.nativeLibraryDir`. That means the AOT path failed before LiteRT invocation and could not prove whether the APK actually contained usable Qualcomm libraries.

## Correction

- Keep the existing strict native-library gate.
- First use `ApplicationInfo.nativeLibraryDir` when all required Qualcomm libraries are present.
- If Android exposes a different installed-APK layout, locate the app's own signed base/split APKs and extract the exact arm64 Qualcomm runtime entries into an app-private directory.
- Point `ADSP_LIBRARY_PATH` and LiteRT `DispatchLibraryDir` at that verified directory.
- Refuse missing, empty, oversized, or incomplete runtime entries. No network download occurs at runtime.
- Preserve deterministic fallback and the hard Ben resource governor.
- CI now asserts the complete Qualcomm runtime set, including QnnHtpPrepare/QnnIr/QnnSaver, in all generated APK variants.

## Important limitation

This source correction does not prove Qualcomm NPU execution. Android/CI compilation and device execution remain separate gates. The next device diagnostic must show `runtimeGate=PASS` and actual runtime inventory hashes before any Qualcomm `CompiledModel.run()` result is interpreted.
