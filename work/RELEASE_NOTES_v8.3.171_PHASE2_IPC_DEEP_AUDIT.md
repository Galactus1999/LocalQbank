# Rovex v8.3.171 — Phase 2 IPC/FGS Deep Audit Correction

## CI correction
- Removed stale nested `v167_stage` Android project from the release source. The previous CI log selected that nested project because the workflow searched for the first `settings.gradle.kts`, so CI compiled stale v8.3.167 code and reported the obsolete `Notification.Builder.setSilent()` error.
- The release ZIP now contains exactly one Android project root.
- `setSilent()` was removed from the notification builder because it is not available on the project API surface; the notification channel remains `IMPORTANCE_MIN`.

## IPC / foreground-service correction
- The client now uses `startForegroundService()` before binding.
- The service promotes itself immediately in `onStartCommand()` before Binder/native work, satisfying the started-FGS lifecycle rather than waiting for inference admission.
- `BIND_IMPORTANT` remains enabled; `BIND_NOT_FOREGROUND` remains absent.
- Foreground promotion is idempotent and explicitly tracked.
- Wake-lock lifecycle remains bounded and paired with neural work.
- Service remains `START_NOT_STICKY`; isolated runtime is disposable and must not resurrect silently.

## Phase status
- Phase 2: IPC lifecycle/foreground hardening — implementation correction complete; device validation remains required.
- Phase 2B: EmbeddingGemma runtime — direct SM8650/QNN/HTP validation already PASS; warm-lifecycle optimization remains the target.
- Phase 3: contrastive Graph-RAG/verifier-first evidence — foundation retained; no speculative rewrite.
- Phase 4: import/large-content hardening — existing streaming/prepared-statement foundation retained; adversarial device profiling remains.
- Phase 5: contextual SRS remains shadow/evaluation only; no uncontrolled actuation.
- Phase 6: resource governor/warm neural lifecycle — bounded warm retention retained; benchmark before any quantization or backend change.
