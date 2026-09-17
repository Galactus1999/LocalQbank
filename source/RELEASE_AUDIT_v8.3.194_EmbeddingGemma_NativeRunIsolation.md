# Rovex v8.3.194 — EmbeddingGemma Native-Run Isolation Audit

- Source baseline: v8.3.193.
- Real device trace reached `WORKER_EMBEDDING_INFERENCE_BEGIN` and then stopped with no Kotlin exception event. This is consistent with a native `CompiledModel.run()` hang/process death boundary, not coroutine cancellation.
- The real diagnostic now requests `Accelerator.NPU` only for the Qualcomm DISPATCH_OP artifact, matching the current LiteRT NPU guidance for compatible NPU models.
- Added durable markers around each embedding invocation and output read: `INFERENCE_1_BEGIN/PASS`, `INFERENCE_2_BEGIN/PASS`, `INFERENCE_3_BEGIN/PASS`, `NATIVE_RUN_BEGIN/PASS`, and `OUTPUT_READ_BEGIN/PASS`.
- Isolated diagnostic client timeout reduced from 150 s to 60 s because runtime initialization has already completed before the native invocation stage; this prevents a native stall from leaving the UI waiting for 2.5 minutes.
- Stale-journal recovery classifies an EmbeddingGemma journal that died after `WORKER_EMBEDDING_INFERENCE_BEGIN` as `EMBEDDING_NATIVE_INFERENCE_STALL_OR_PROCESS_DEATH` after 30 s.
- No transport/FGS architecture changes.
- No study-data path changes.
