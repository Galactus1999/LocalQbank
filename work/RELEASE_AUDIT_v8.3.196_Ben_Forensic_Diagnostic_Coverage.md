# RELEASE AUDIT v8.3.196 — Ben Forensic Diagnostic Coverage

## Baseline
Started from the corrected v8.3.195 EmbeddingGemma post-inference lifecycle source.

## Version
- versionName: 8.3.196
- versionCode: 291

## Scope
No change to the proven Qualcomm/LiteRT NPU inference implementation.
Changes are limited to:
- durable forensic stage instrumentation;
- post-inference/semantic/report/cleanup stage boundaries;
- worker lifecycle evidence;
- diagnostic survival across worker unbind for isolated EmbeddingGemma diagnostics;
- one-click export inclusion of live durable journal;
- deterministic failure classification;
- future failure gate coverage.

## Diagnostic gates
1. Artifact hash
2. Tokenizer parse
3. Graph contract
4. Qualcomm runtime inventory
5. QNN provider
6. CompiledModel creation
7. Tensor contract
8. Input buffer write
9. Native run
10. Output buffer read
11. Embedding dimension
12. Finite-value validation
13. Embedding norm
14. Semantic cosine/separation
15. RAM
16. Java heap
17. Native heap
18. Thermal state
19. Power-save state
20. FGS admission/promotion
21. Binder lifecycle
22. Worker create/unbind/destroy
23. Watchdog
24. Terminal Binder reply
25. Native runtime cleanup

## Native-death escalation
If the worker dies before terminal completion, the report explicitly records the last durable stage and recommends host-level evidence:
logcat/tombstone/bugreport, LiteRT run_model CPU/NPU A-B, QNN native reproduction, Saver/IR graph dumps, culprit_finder, AOT-vs-JIT comparison, and exact LiteRT/QAIRT library inventory.

## Static checks
- XML parsing: 28 layouts/resources checked; 0 parse failures.
- Duplicate IDs: 0 within individual XML files.
- Production GlobalScope: 0.
- Production Thread.sleep: 0.
- Production runBlocking: 0. Existing test-only runBlocking remains unchanged.
- BIND_NOT_FOREGROUND: 0.
- setSilent: 0.
- broad production catch(Throwable): 0.
- stale v8.3.195 diagnosticRuntimeHold reference: 0.
- Changed-file structural delimiter scan: reviewed.
- Android/Kotlin compilation: NOT locally verified because Gradle tooling/network is unavailable in this environment. CI remains mandatory.

## Important architecture decision
The EmbeddingGemma Qualcomm NPU path remains unchanged because real-device evidence shows three consecutive native runs completing at approximately 35/30/29 ms with 768-float outputs. Further model/backend rewrites would destroy useful evidence and are not justified until a native-stage failure is actually demonstrated.
