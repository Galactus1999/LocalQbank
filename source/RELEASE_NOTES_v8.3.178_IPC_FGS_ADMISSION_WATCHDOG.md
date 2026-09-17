# Rovex v8.3.178 — IPC FGS-admission wedge hardening

- Version: `8.3.178`
- versionCode: `275`
- Baseline: v8.3.177 / versionCode 274
- Package/applicationId: `com.localqbank.library`

## Root-cause correction
The v8.3.177 device failure still stopped at `Remote diagnostic accepted by isolated Ben process`. In v8.3.177 the isolated service created its server watchdog only **after** `beginForegroundWork()`. That left an unbounded pre-watchdog window between Messenger acceptance and foreground admission.

v8.3.178 starts the server watchdog immediately after acceptance and adds explicit diagnostic progress stages around foreground-service admission:

- `accepted`
- `fgs_admission`
- `foreground_ready`
- `admission`
- diagnostic engine progress
- `complete`

If FGS admission wedges, the isolated process is now bounded and the client receives the precise server stage when possible.

## Safety/architecture
- No EmbeddingGemma runtime changes.
- No increase to the 150 s client timeout.
- The isolated process remains disposable.
- Existing single-flight, cancellation, FGS, wake-lock, generation and terminal-CAS mechanisms are preserved.
- The watchdog begins before any potentially blocking foreground-admission call.

## Verification
Static/source audits are required before release. Android/CI compilation and physical-device runtime are not claimed verified until CI/device testing actually passes.
