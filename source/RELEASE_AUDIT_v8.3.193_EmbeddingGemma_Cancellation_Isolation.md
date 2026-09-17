# Rovex v8.3.193 — EmbeddingGemma Cancellation Isolation

## Root cause targeted
The v8.3.192 device diagnostic reached LiteRT/Qualcomm/NPU setup and then terminated with `JobCancellationException` immediately after entering the 3-embedding smoke test. The isolated diagnostic now runs on a dedicated worker diagnostic scope and wraps the long diagnostic execution in `NonCancellable`, preventing a parent coroutine cancellation boundary from discarding the native diagnostic result.

## Changes
- Dedicated `diagnosticScope` for the real EmbeddingGemma isolated diagnostic.
- Existing ordinary inference `scope` remains unchanged.
- `onDestroy()` cancels both scopes.
- Added durable `WORKER_EMBEDDING_INFERENCE_BEGIN` stage marker immediately before the smoke-test inference.
- Explicitly classify native/diagnostic `CancellationException` separately from ordinary runtime exceptions.
- Existing proven FGS/Binder transport path unchanged.
- Version 8.3.193 / versionCode 288.

## Verification
- Static source inspection performed.
- No new `GlobalScope`, `runBlocking`, `Thread.sleep`, or BIND_NOT_FOREGROUND introduced.
- Full Android Gradle compilation requires CI; local environment may not have required Gradle/dependency artifacts.
