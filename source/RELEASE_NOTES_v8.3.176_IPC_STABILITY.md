# Rovex v8.3.176 — Ben IPC Stability Correction

- Keeps the proven EmbeddingGemma direct LiteRT/Qualcomm path unchanged.
- Starts the foreground service before establishing Binder for real neural work; pure `CMD_PING` remains Binder-only.
- Uses the Android 14+ `bindService(..., Executor, ServiceConnection)` overload to dispatch service callbacks onto Ben's dedicated callback thread, avoiding main-looper coupling.
- Adds an explicit isolated-diagnostic acceptance/progress stage before native work.
- Surfaces failed Binder reply delivery instead of silently swallowing `RemoteException`.
- Adds a transport-only instrumented Binder ping test that requires neither QBank data nor a neural model.
- Corrects version identity to 8.3.176 / versionCode 273.
- Existing Binder death recovery, generation fencing, cancellation, exactly-once terminal state, bounded payloads, and deterministic fallback remain intact.
