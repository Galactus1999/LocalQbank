# Rovex v8.3.182 — IPC Deep Hardening

VersionCode: 279

## IPC/FGS corrections
- Added an explicit Binder FGS-admission handshake so a real IPC command cannot race ahead of `startForeground()` promotion.
- Bind intents now carry the FGS request marker; `onBind()` can perform prompt admission even if `onStartCommand()` and binding callbacks race.
- Added a bounded main-thread promotion bridge for Binder callbacks and explicit `REPLY_FGS_ADMISSION` diagnostics.
- Preserved Android 14 `specialUse` FGS type and manifest permission; no invalid `BIND_IMPORTANT` fallback is used.
- Added automatic one-shot reconnect/rebind with exactly one request replay for transport/process-death/admission failures. Semantic model/resource failures are not retried.
- Added shared-memory bulk transport for large UTF-8 prompt/query/report/text payloads. Binder remains the control plane; SharedMemory carries payloads above 32 KiB.
- Added bounded token coalescing (50 ms / 8 KiB) to prevent unbounded Messenger/UI message flooding.
- Added a pure JVM retry-policy contract test and a deterministic fake process transport for JVM lifecycle/race coverage.
- Added an Android instrumentation SharedMemory round-trip test that does not require model initialization.

## Stability constraints
- No native backend changes.
- Deterministic fallback remains authoritative.
- No broad `catch(Throwable)` added.
- Whole-project XML, duplicate-ID, findViewById/XML, startup-risk, static anti-pattern, version, dependency, and source scans are required before CI release.

## Build status
Android/CI compilation is **not claimed green** until CI actually builds v8.3.182.
