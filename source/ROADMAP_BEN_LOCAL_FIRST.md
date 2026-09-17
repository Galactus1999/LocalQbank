# Ben / Rovex Local-First Intelligence Roadmap

## Completed in v8.3.144
1. Binder-aware inference client with DeathRecipient.
2. Service-generation fencing against stale callbacks.
3. Cancellable coroutine-to-Binder generation bridge.
4. Exactly-once terminal delivery on both client and service sides.
5. Explicit remote cancellation from collector/UI cancellation.
6. Single-flight latest-request-wins generation barrier.
7. LiteRT-LM native async streaming path.
8. Native `Conversation.cancelProcess()` integration.
9. Cancellation grace watchdog; isolated process remains the disposable crash boundary.
10. Thermal listener integration and policy-driven prompt degradation.
11. Severe/critical thermal cancellation and deterministic fallback.
12. Grounded pipeline migration to cancellable generation.
13. Adaptive Engine visibility of IPC/thermal controls and status.

## Phase 2 — device validation
- Verify 1→2→3 rapid-card swipe cancellation.
- Verify Binder death during prefill/decode.
- Verify reconnect and stale-generation callback rejection.
- Measure cancellation-to-release latency.
- Measure cold/warm Gemma latency and thermal response on SM8650.
- Verify isolated-process restart and deterministic fallback.

## Phase 3 — contrastive Graph-RAG
- Extract MCQ distractors as separate retrieval queries.
- Retrieve bounded distractor evidence through the existing graph index.
- Add `<QUESTION_EVIDENCE>`, `<DISTRACTOR_EVIDENCE>`, and `<ANSWER_EVIDENCE>` sections.
- Keep distractor evidence retrieval-only; verifier remains clinical authority.
- Add adversarial/exclusion-focused NEET-PG/INI-CET tests.

## Phase 4 — import pipeline hardening
- Audit APKG/HTML allocation hotspots with profiling data.
- Introduce prepared SQLite statements where they measurably reduce allocations.
- Preserve current batched transactional/resumable design.
- Never trade importer correctness for allocation reduction.

## Phase 5 — adaptive learning
- Persist contextual-bandit state behind an explicit feature flag.
- A/B against the existing SRS scheduler.
- Require rollback, minimum sample counts and deterministic fallback.
- Only promote to scheduler authority after measurable improvement.

## Phase 6 — neural acceleration experiments
- Capability-probe the exact Gemma artifact for MTP/speculative decoding.
- Benchmark MTP OFF/ON on the target SM8650 device before enabling.
- Keep LiteRT-LM responsible for internal KV-cache implementation.
- Consider zero-copy IPC only if profiling demonstrates that the current ~3 KB embedding IPC is material.
