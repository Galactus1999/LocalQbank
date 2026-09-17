# Rovex 8.3.140 — Ben Qualcomm Runtime Gate Completeness

VersionCode: 238

## Bug found during audit

`BenEmbeddingGemmaEngine.Runtime` extracted 8 Qualcomm libraries in
`ensureQualcommRuntimeDirectory()`:

```
libLiteRtDispatch_Qualcomm.so, libQnnSystem.so, libQnnHtp.so,
libQnnHtpPrepare.so, libQnnIr.so, libQnnSaver.so,
libQnnHtpV75Stub.so, libQnnHtpV75Skel.so
```

but the two functions responsible for *validating* that extraction —
`qualcommRequiredRuntimeLibraries()` (which gates `backendDetailsValue +=
"; runtimeGate=PASS"`) and `qualcommRuntimeInventory()` (used in the device
diagnostic report) — only ever checked 5 of those 8 names. `libQnnHtpPrepare.so`,
`libQnnIr.so`, and `libQnnSaver.so` could be missing or corrupt and the engine
would still report `runtimeGate=PASS` and a "complete" runtime inventory. The
first sign of trouble would have been an opaque native failure out of
`CompiledModel.create()` instead of the clear, actionable
`IllegalStateException` this gate exists to produce.

## Fix

All three functions now share a single `qualcommRuntimeLibraryNames` list
(8 entries) as the source of truth, so extraction, gating, and diagnostic
reporting can never drift out of sync again.

That list is declared **above** `init{}` in the `Runtime` class, next to the
existing `qualcommRuntimeLock` — for the same reason `qualcommRuntimeLock` had
to move there in v8.3.138. `ensureQualcommRuntimeDirectory()` is called from
`init{}` and reads this property; Kotlin initializes properties in source
order, so declaring it after `init{}` would have left its backing field
`null` on first construction and thrown a `NullPointerException` instead of
the intended fail-closed error path. (An earlier attempt at this exact fix,
during audit, reintroduced that precise bug before being caught and moved.)

## Scope

`app/src/main/java/com/localqbank/library/BenEmbeddingGemmaEngine.kt` only.
No change to the EmbeddingGemma tensor contract, Qualcomm AOT dispatch
strategy, deterministic Ben/RAG fallback, or model artifacts.

## Verification status

Static source changes can be audited locally; not exercised on-device in this
environment. Qualcomm NPU execution is **not claimed working** until a
v8.3.140 device diagnostic shows `runtimeGate=PASS` with all 8 libraries
present and successful inference.
