# Rovex v8.3.144 — Ben Inference IPC Hardening

## Scope
Focused hardening of the existing `:inference_process` boundary. No new business-logic owner is introduced.

## Implemented
- `BenInferenceProcessClient` now uses a cancellable streaming bridge for Gemma generation.
- Binder `DeathRecipient` is attached to every bound service generation.
- Client service-generation IDs reject stale callbacks after reconnect.
- Per-request terminal state uses compare-and-set so only one terminal event is delivered.
- UI/collector cancellation sends an explicit remote `CMD_CANCEL`.
- One-shot IPC requests also become cancellation-aware and are failed on Binder death.
- `BenInferenceProcessService` is single-flight for generation.
- New generation requests cancel the active request before entering the `Mutex` barrier.
- Native LiteRT-LM generation now uses the supported asynchronous Flow API.
- Service-side cancellation calls LiteRT-LM `Conversation.cancelProcess()` and waits for the generation Flow to terminate before the single-flight lock is released.
- A 2.5-second cancellation grace watchdog terminates only the disposable isolated inference process if native cancellation fails to release, preventing an indefinite single-flight wedge.
- Thermal severe/critical states cancel active generation and deny new neural generation.
- Moderate thermal state reduces the prompt budget from 8,000 to 5,000 characters before generation.
- `EngineHealthManager` observes Android thermal status through `PowerManager` and exposes it in its health snapshot.
- Grounded neural pipeline now consumes the cancellable streaming generation path and deterministically falls back on process death/cancellation/failure.

## Deliberately not claimed
- No arbitrary JNI `AtomicBoolean` was invented: LiteRT-LM 0.16.1 already exposes native cancellation through `Conversation.cancelProcess()`.
- No fake native lock release is claimed. The service's `Mutex` is released only after the LiteRT-LM Flow terminates.
- No speculative decoding, KV-cache quantization, AHardwareBuffer IPC, or SRS scheduler mutation was introduced here.
- Android compilation/CI is not claimed green until the actual CI build passes.
