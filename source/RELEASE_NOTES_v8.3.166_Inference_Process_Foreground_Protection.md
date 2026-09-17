# Rovex 8.3.166 — Inference Process Foreground Protection

VersionCode: 263

## What the device screenshot actually showed

`Rovex Test & Diagnostics Center` reported:

```
FAILED • IPC_TIMEOUT_90000MS
```

That exact string cannot be produced by v8.3.165's source. `DIAGNOSTIC_CLIENT_TIMEOUT_MS`
in `BenInferenceProcessClient.kt` is `150_000L`, not `90000` -- there is no `90000`
constant anywhere in this codebase (confirmed by search). A `90`-second diagnostic
client timeout only ever existed in v8.3.163/164. **The screenshot is evidence the
installed APK predates v8.3.165's timeout fix, not evidence that v8.3.165 itself is
still broken.** Rebuild and reinstall before retesting; the failure string alone will
tell you which build actually ran (`IPC_TIMEOUT_150000MS` if the client-side timeout
is still hit, `SERVER_DIAGNOSTIC_HARD_TIMEOUT_STAGE=...` if the isolated process's own
120-second deadline fires first).

## A real latent bug found while investigating

While auditing why a diagnostic could stall long enough to hit either timeout at all,
`BenInferenceProcessService` (the `:inference_process` process that actually runs
EmbeddingGemma/Qualcomm HTP) had nothing protecting it from OS/OEM background-process
management:

- No foreground service promotion.
- No wake lock.

A background process doing 20-150+ seconds of sustained NPU/CPU work with neither is a
well-known target for aggressive OEM battery managers (OnePlus/OxygenOS in particular)
to freeze or heavily throttle -- the process stays alive from Binder's point of view (no
`DeathRecipient` fires, so the client never sees `INFERENCE_PROCESS_DIED`), it just stops
making progress. That failure signature -- clean client-side timeout, no process-death
event, `Stage: IDLE` the whole time -- is indistinguishable from what the reported
screenshot showed, independent of whether the stale-build explanation above is the whole
story.

## Fix

`BenInferenceProcessService` now promotes itself to foreground priority only while a
generation or diagnostic request is actually in flight:

- `beginForegroundWork()` / `endForegroundWork()`, reference-counted so overlapping
  requests don't fight over foreground state, called from the existing `finally` blocks
  in `startGeneration()` and `startOneShot()` so every promotion is guaranteed to be
  demoted.
- A minimum-importance, silent notification channel (`IMPORTANCE_MIN`, no badge) backs
  the required foreground notification; it's only visible while a test or generation is
  actually running.
- A `PARTIAL_WAKE_LOCK`, capped at 130s (slightly longer than the 120s server hard
  deadline) as a backstop against a leaked lock, released in the same `finally` blocks
  and defensively again in `onDestroy()`.
- Declared `android:foregroundServiceType="specialUse"` with the required Android 14+
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property, and added the `WAKE_LOCK`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `POST_NOTIFICATIONS`
  manifest permissions.

All promotion/lock calls are wrapped in `runCatching` and log to `BenNeuralTelemetry` on
failure rather than crashing the diagnostic path -- if a device or OS version disallows
the foreground promotion for some reason, the service still runs, just without this
protection, exactly as it did before this change.

## Scope

`AndroidManifest.xml` and `BenInferenceProcessService.kt` only. No change to
`BenInferenceProcessClient.kt`'s timeouts (already correct in v8.3.165), the
EmbeddingGemma tensor contract, Qualcomm AOT dispatch strategy, or deterministic
Ben/RAG fallback.

## Verification status

Static source changes audited locally; not exercised on-device in this environment.
Confirm on v8.3.166: (1) the diagnostic either passes or fails with a reason string
that actually appears in this source, never `IPC_TIMEOUT_90000MS`; (2) if it still
times out, check whether a persistent low-priority "Ben is running an on-device test"
notification was visible during the run -- its absence would mean the foreground
promotion itself failed (check `BenNeuralTelemetry` for the logged reason) and points
back to a device/OS-level restriction rather than this service's own logic.
