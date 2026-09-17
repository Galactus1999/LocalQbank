# Rovex v8.3.125 — Adaptive Control Room + EmbeddingGemma Diagnostic Hold

## Scope
- Redesigned Settings → Adaptive Engine into organized control-room sections with collapsible subsections.
- Grouped Adaptive learning, strategy, Ben cognition, Neural Lab, resource governor, battery, resilience, companion engines, activity and safety boundaries.
- Preserved authoritative manager ownership; Settings remains presentation/control only.
- Added a non-destructive EmbeddingGemma diagnostic that validates the installed model/tokenizer, tensor contract, selected backend and a semantic-similarity pair off the UI thread.
- Added Reciprocal Rank Fusion (RRF) at the grounded Ben orchestration boundary so lexical QBank recall and semantic EmbeddingGemma ranking are fused without fragile score averaging.
- Kept EmbeddingGemma optional and governor-gated; deterministic RAG remains authoritative.
- Gemma 3 270M remains the optional generation accelerator.

## EmbeddingGemma next-stage direction
1. Validate the installed 512-token `.tflite` graph + official `sentencepiece.model` on the real device.
2. Capture backend/contract/latency/memory telemetry.
3. Run semantic-pair and retrieval-reranking tests.
4. Only after successful device validation, connect embeddings to Ben's hybrid retrieval layer using lexical retrieval + semantic reranking/RRF.
5. Precompute document embeddings in bounded background batches; never embed the full QBank synchronously during study.
6. Keep deterministic retrieval as the hard fallback.

## Stability
- Live Adaptive telemetry remains lifecycle-scoped and off the UI thread.
- No model initialization occurs from Activity startup.
- No new persistence/business-logic owner was introduced.
- Android/CI green status is intentionally not claimed until CI/device tests pass.
