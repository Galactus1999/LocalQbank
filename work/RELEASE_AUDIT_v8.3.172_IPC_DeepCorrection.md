# Rovex v8.3.172 — IPC Deep Correction

## Trigger
v8.3.171 still produced `IPC_TIMEOUT_150000MS` on the isolated Ben diagnostic while the direct EmbeddingGemma control path was proven PASS on the SM8650.

## Corrections
- Split pure Binder establishment from foreground-service startup.
- `ensureBound()` now performs only `bindService(BIND_AUTO_CREATE | BIND_IMPORTANT)`.
- Non-ping IPC operations explicitly call `startForegroundService()` only after Binder is established.
- `CMD_PING` remains a zero-native-work Binder control-plane probe and does not require FGS promotion.
- FGS promotion explicitly passes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 29+.
- Added explicit FGS-start failure reporting (`IPC_FGS_START_FAILED`).
- IPC timeout diagnostics retain the latest remote progress stage.
- Isolated-service one-shot terminal responses are CAS-protected against duplicate timeout/success replies.
- Server diagnostic pre-timeout publishes the current stage; isolated process is killed after a short 2.5 s terminal-delivery grace rather than waiting an additional 20 s.

## Evidence preserved
Direct EmbeddingGemma control test remains authoritative and is not modified. The user's SM8650 report established:
- Qualcomm QNN/HTP/NPU selected
- CompiledModel creation PASS
- inference PASS
- ~19.7 s cold initialization
- ~102 ms inference
- semantic smoke test PASS

## Audit
- XML: 27 files parsed, 0 errors
- Duplicate IDs within individual layouts: 0
- runBlocking/GlobalScope/Thread.sleep/broad catch(Throwable): 0
- BIND_NOT_FOREGROUND: 0
- setSilent: 0
- settings.gradle.kts: exactly 1
- architecture cohesion: PASS with existing large-file warnings
- architecture regression: 51 singleton/object declarations vs historical baseline 44; intentionally not rebaselined
- Android/CI compilation: not claimed until CI executes
