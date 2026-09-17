# Rovex v8.3.182 — IPC Deep Hardening Audit

## Baseline
- Starting source: v8.3.181 / versionCode 278
- New source: v8.3.182 / versionCode 279
- Package/applicationId: com.localqbank.library

## Implemented
1. FGS admission handshake (`CMD_FGS_ADMISSION` / `REPLY_FGS_ADMISSION`).
2. Bind intent carries the foreground-request marker; `onBind()` can synchronously promote FGS before returning the Binder.
3. Binder callback thread can request main-thread FGS promotion through a bounded 2-second latch; no main-thread wait is introduced.
4. Android 14+ uses `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`; pre-34 uses the legacy two-argument `startForeground()` path.
5. Explicit FGS-start exception classification: start-not-allowed, missing/invalid type, security exception, generic start failure.
6. One-shot transport recovery: exactly one replay after process death, disconnect, send/bind failure, remote-not-ready, or FGS-not-ready timeout. No retry for payload, semantic model, or fatal FGS configuration errors.
7. SharedMemory bulk text transport for payloads above 32 KiB, with read-only receiver mapping and bounded 8 MiB payload limit. Binder remains the control plane.
8. SharedMemory support covers generation prompts and selected query/compare/report/final-text/error payloads.
9. Token emission is coalesced to a 50 ms / 8 KiB bounded stream window, preventing unbounded Messenger/UI message flooding.
10. JVM fake process transport and retry-policy tests added; real SharedMemory round-trip instrumentation test added.

## Static audits
- 27 XML/manifest files parsed successfully.
- Duplicate IDs within individual XML layouts: 0.
- Production `catch(Throwable)`: 0.
- Production `GlobalScope`: 0.
- Production `Thread.sleep`: 0.
- Production `BIND_NOT_FOREGROUND`: 0.
- Production `setSilent`: 0.
- Kotlin structural balance for changed IPC sources: PASS.
- Pure Kotlin compile of retry policy + fake transport: PASS.
- Architecture cohesion audit: PASS, with existing large-file warnings.
- Architecture regression audit: same pre-existing singleton/object count of 51 versus historical baseline 44; v8.3.181 also reports 51, so v8.3.182 did not introduce this regression.
- `findViewById` scan: two apparent TextView/custom-view hits for `RovexColorFlowTextView`; source confirms the custom view extends `TextView`, so no actual type mismatch.
- Full Android compilation: NOT VERIFIED because Gradle 9.3.1 distribution download fails in the environment with DNS resolution failure for downloads.gradle.org.

## Runtime status
The previous device result `FAILED IPC_FGS_NOT_READY` is addressed architecturally by requiring an explicit foreground-admission acknowledgement before real IPC work is sent. Actual device validation is still required.
