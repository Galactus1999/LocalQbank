# Rovex v8.3.197 — Ben Diagnostic State Reconciliation

## Purpose
Correct the diagnostic-result inconsistency exposed by the v8.3.196 OnePlus CPH2691 forensic export.

The device trace proved the isolated EmbeddingGemma execution completed through:
- Qualcomm/NPU CompiledModel creation
- three native inference runs
- output validation
- semantic scoring
- report construction
- runtime cleanup
- worker terminal Binder reply

Yet the top-level export still reported `latestDiagnosticStatus=IPC_FAILED`. v8.3.197 makes the durable journal authoritative for terminal reconciliation and distinguishes worker-side completion from end-to-end client acknowledgement.

## Changes
- Added durable `CLIENT_EMBEDDING_REPLY_RECEIVED` and generic `CLIENT_TERMINAL_REPLY_RECEIVED` markers.
- Added worker-reply/client-ack distinction:
  - `PASS` = client terminal reply successfully decoded/accepted.
  - `PARTIAL_PASS` = worker sent the terminal reply but the main-process client acknowledgement was not durably observed.
- Added `DIAGNOSTIC_TERMINAL_RECONCILED` and idempotent terminal handling.
- Added a 2-second reconciliation grace window to avoid racing an in-flight client callback.
- Added `diagnosticStatusConsistency` to one-click exports.
- `RovexDiagnosticsStore.publishIpc()` now derives status from the explicit `result=` field instead of treating every non-`PASS` string as failure.
- IPC coordinator reports now publish through the IPC-specific store path so a partial/failed IPC diagnostic cannot be overwritten as generic `COMPLETE`.
- Diagnostic `TEST_START` now records diagnostic schema version, app version name/code, and test kind.
- Terminal report generation can recover the diagnostic kind from the durable trace after application-process interruption.
- Terminal finalization is idempotent and preserves a previously reconciled terminal result instead of overwriting it with a later stale callback outcome.
- One-click export reconciles durable evidence before reading status/report state.

## Important interpretation of the supplied v8.3.196 trace
The supplied trace ended at `WORKER_EMBEDDING_REPLY_PASS` and did not contain `CLIENT_EMBEDDING_REPLY_PASS`. Therefore it proves worker-side execution and reply transmission, but not complete client-side acknowledgement. This release records that distinction instead of misclassifying it as a model/NPU failure or silently calling it end-to-end PASS.

## Stability constraints
- No EmbeddingGemma model, tokenizer, QNN provider, LiteRT execution path, FGS architecture, or worker process architecture was changed.
- No study-data path was changed.
- No new background scheduler, wake lock, heavyweight model, or business-logic owner was introduced.
