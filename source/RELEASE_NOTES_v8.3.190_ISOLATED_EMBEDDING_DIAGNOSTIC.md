# Rovex v8.3.190 — Real Isolated EmbeddingGemma Diagnostic

## Purpose
Use the proven v8.3.189 Binder/FGS transport path as a control, then execute the existing EmbeddingGemma diagnostic inside the worker process. This separates transport/lifecycle failures from model/runtime failures.

## Key design
- v8.3.189 model-free `CMD_DIAGNOSTIC` remains unchanged and is treated as the transport control.
- Added `CMD_ISOLATED_EMBEDDING_DIAGNOSTIC` with a dedicated reply code.
- Single-attempt execution: no automatic retry can hide a native/runtime failure.
- Durable journal event is written before model/runtime stages.
- Existing `BenEmbeddingGemmaEngine.diagnosticReport()` is reused; no duplicate model business logic is introduced.
- The test remains visible in Test & Diagnostics and is accessible from Ben Model Lab.
- A successful report includes the full EmbeddingGemma contract/backend/inference/semantic report plus the IPC trace.
- A worker process death should leave the last journal stage available for recovery.

## Safety
- No study data is modified.
- Existing AppManagers/engine ownership is preserved.
- FGS remains main-process owned.
- Worker remains a normal secondary application process.
- Model execution remains governed by the existing Ben resource/thermal policy.
