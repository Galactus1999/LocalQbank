# Rovex v8.3.192 — EmbeddingGemma lifecycle compile + worker lifetime correction

- Corrected `BenIpcDiagnosticRecorder.recoverStale()` call to pass the diagnostic `kind` into `buildReport()`.
- Hardened the real isolated EmbeddingGemma diagnostic worker lifetime: the worker service is explicitly STARTED for the duration of the diagnostic, rather than relying only on a binding.
- This prevents `Service.onUnbind()` from becoming an accidental cancellation boundary while the model/runtime is executing. The existing worker `onUnbind()` cancellation behavior for ordinary bound inference remains unchanged.
- Added durable `CLIENT_WORKER_START_PASS/FAIL` markers.
- Preserved the proven FGS/Binder transport path and the Activity-independent application-lifetime diagnostic coordinator.
- VersionCode 287 / VersionName 8.3.192.
