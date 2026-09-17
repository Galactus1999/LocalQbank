# v8.3.191 — Isolated EmbeddingGemma Diagnostic Lifecycle Fix

- Moved the real isolated EmbeddingGemma diagnostic owner out of `RovexDiagnosticsDataCenter`'s Activity `lifecycleScope`.
- Added `BenIsolatedEmbeddingDiagnosticCoordinator`, a main-process application-lifetime coordinator using `SupervisorJob + Dispatchers.IO`.
- Activity is now presentation-only: it starts/stops the coordinator and polls the durable journal for display.
- Activity recreation/window interruption no longer cancels the remote model diagnostic.
- User-requested STOP remains explicit and cancels the diagnostic normally.
- Extended the durable diagnostic journal with a diagnostic kind so EmbeddingGemma reports are not mislabeled as transport-only IPC reports.
- Preserved the proven v8.3.189 Binder/FGS transport path unchanged.
- Preserved the real EmbeddingGemma engine, LiteRT, tokenizer and Qualcomm/NPU execution path unchanged.
