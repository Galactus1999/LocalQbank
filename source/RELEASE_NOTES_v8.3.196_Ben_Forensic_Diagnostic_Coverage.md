# Rovex v8.3.196 — Ben EmbeddingGemma Forensic Diagnostic Coverage

## Purpose
This release stops the iterative “patch one unknown failure at a time” diagnostic cycle. The EmbeddingGemma diagnostic now records durable evidence for the full execution chain and classifies post-failure stages.

## Device evidence carried forward
On OnePlus CPH2691 / Android SDK 36, the Qualcomm NPU path has repeatedly completed three native EmbeddingGemma executions:
- inference 1: 35–40 ms
- inference 2: 30–31 ms
- inference 3: 29–31 ms
- all outputs: 768 FLOAT values

The diagnostic therefore does not rewrite the proven NPU execution path.

## New durable forensic stages
artifact hash → tokenizer parse → graph contract → Qualcomm runtime inventory → QNN provider → CompiledModel creation → tensor contract → input write → native run → output read → embedding dimension/finite validation → semantic scoring → report build → terminal Binder reply → runtime cleanup → worker lifecycle.

Each stage is journaled before and after the operation where possible.

## Failure classification
The journal now distinguishes:
- EMBEDDING_NATIVE_RUN_STALL_OR_PROCESS_DEATH
- EMBEDDING_OUTPUT_READ_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_POST_INFERENCE_TRANSITION_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_OUTPUT_VALIDATION_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_SEMANTIC_SCORING_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_REPORT_BUILD_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_TERMINAL_REPLY_FAILURE_OR_PROCESS_DEATH
- EMBEDDING_UNKNOWN_POST_STAGE_FAILURE_OR_PROCESS_DEATH

Worker create/unbind/destroy and PID evidence are also retained.

## One-click export
The Diagnostics Center export now includes both the stored report and the live durable journal when a run is still active/recovering, plus forensic classification and future failure coverage.

## External diagnostic strategy
For genuine Qualcomm native/process-death failures, app-side evidence is not sufficient to recover a native tombstone. The intended next-level host investigation is:
1. CPU vs NPU A/B execution.
2. LiteRT `run_model` reproduction.
3. QNN native `qnn-net-run` reproduction where available.
4. LiteRT Qualcomm Saver/IR graph artifacts.
5. `culprit_finder` binary search if a graph/operator-specific failure remains.
6. AOT vs JIT comparison and exact LiteRT/QAIRT library-set capture.
7. Android logcat/tombstone/bugreport capture when the worker process dies natively.

This follows the current LiteRT Qualcomm debugging facilities rather than introducing another blind application-layer patch.
