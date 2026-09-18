# Rovex v8.3.175 — IPC CI Compiler + Lifecycle Correction

## CI failure corrected
The v8.3.174 CI build reached `:app:compileDebugKotlin` and failed at `BenInferenceProcessClient.kt:518:40` because `pendingBindContinuation` was typed as `kotlin.coroutines.Continuation<Messenger>` while the code used the coroutine-specific `isActive` property.

The field is now typed as `CancellableContinuation<Messenger>`, matching the value produced by `suspendCancellableCoroutine` and preserving cancellation-aware race handling.

## Additional deep-audit correction
When the final foreground neural operation ended, the service previously removed its foreground status but remained in the started-service state. v8.3.175 calls `stopSelf()` after the final active operation, while existing bindings can still keep the service alive for the bounded warm-runtime window.

No EmbeddingGemma runtime/model changes were made. The proven direct SM8650 NPU control path remains authoritative.
