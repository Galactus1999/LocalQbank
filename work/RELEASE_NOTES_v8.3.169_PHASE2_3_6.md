# Rovex v8.3.171 — Ben Phase 2/3/6 Consolidation

VersionCode: 266

## Evidence baseline
- EmbeddingGemma 300M direct control test passed on OnePlus CPH2691 / Qualcomm SM8650.
- LiteRT CompiledModel Qualcomm NPU/HTP path passed.
- QNN available; CPU/XNNPACK fallback was false.
- Cold runtime initialization measured ~19.7 s; inference ~102 ms; semantic smoke test passed.

## Phase 2 — IPC lifecycle hardening
- BenInferenceProcessClient now explicitly starts BenInferenceProcessService before binding.
- This establishes the documented started+bound service lifecycle required for reliable foreground promotion.
- Removed BIND_NOT_FOREGROUND from the neural service binding path.
- Service is START_NOT_STICKY; intentional client release stops the service.
- Foreground promotion remains request-scoped with notification + bounded partial wake lock.
- Binder death/generation fencing and single-flight semantics remain intact.

## Phase 2B / Phase 6 — warm runtime lifecycle
- Isolated service keeps initialized native runtimes warm for a bounded 120 s idle window after an unexpected unbind.
- Warm retention is evidence-driven by the measured ~20 s cold start / ~100 ms warm inference.
- Runtime is closed and service stopped after the TTL; no indefinite heavyweight background runtime.
- Explicit client trim/close still releases the service immediately.

## Phase 3 — evidence/verification preservation
- Existing contrastive Graph-RAG remains authoritative through QUESTION_EVIDENCE, bounded DISTRACTOR_EVIDENCE and ANSWER_EVIDENCE.
- Only ANSWER_EVIDENCE enters verifier authority.
- Existing deterministic RRF and verifier-boundary regression tests preserved.
- No new neural source-of-truth introduced.

## Validation
- XML parse: 27 files, 0 errors.
- Per-layout duplicate IDs: 0.
- runBlocking/GlobalScope/Thread.sleep/catch(Throwable): 0 production matches.
- Pure Kotlin compilation of changed pure policy/evidence classes: PASS.
- Architecture cohesion audit: PASS; existing large-file warnings remain.
- Architecture regression audit: still reports 51 object/singleton declarations vs historical baseline 44; not suppressed.
- Full Android Gradle compile: NOT VERIFIED because downloads.gradle.org DNS is unavailable in this environment.
