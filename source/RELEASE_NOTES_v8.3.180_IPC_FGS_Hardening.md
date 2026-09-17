# Rovex v8.3.180 — IPC/FGS hardening

- Foreground-service promotion is synchronous in `onStartCommand()` on the Service main thread; no latch, IO dispatcher, or Binder callback is involved in the Android FGS deadline path.
- `onBind()` has an idempotent fallback promotion when a start/bind ordering race occurs.
- IPC control coroutines use `Dispatchers.Default`; EmbeddingGemma diagnostic code remains responsible for switching heavyweight work to `Dispatchers.IO`.
- FGS failures are terminally surfaced as `IPC_FGS_PROMOTION_FAILED` instead of allowing the diagnostic to remain at `accepted`.
- Notification uses the existing monochrome launcher drawable rather than the adaptive launcher mipmap as a notification small icon.
- Kotlin bind-time continuation checks use `continuation.context[Job]?.isActive == true` to avoid the prior compiler ambiguity.
- No change to the proven direct EmbeddingGemma/NPU runtime.
