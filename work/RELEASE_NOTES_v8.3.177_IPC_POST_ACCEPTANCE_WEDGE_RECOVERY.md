# Rovex v8.3.177 — IPC post-acceptance wedge recovery

- Version: `8.3.177`
- versionCode: `274`
- Baseline: v8.3.176 / versionCode 273
- Package/applicationId: `com.localqbank.library`

## Correction
After the isolated diagnostic has been accepted but becomes silent until the client hard deadline, the client now records request/generation/elapsed-time/last-stage telemetry, reports the stage, attempts cancellation, resumes the waiting coroutine, and immediately calls `trim()` to unbind/stop the potentially wedged isolated service. The next request therefore cannot silently reuse that transport.

## Diagnostics UI
The Test & Diagnostics Center now distinguishes Binder acceptance from post-acceptance silence and explains that an IPC timeout does not by itself prove EmbeddingGemma/model failure.

## Verification
Source-only static checks were performed. Android compilation and device runtime are **not claimed verified** in this environment.
