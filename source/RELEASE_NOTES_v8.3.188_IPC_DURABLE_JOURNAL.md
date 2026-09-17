# Rovex v8.3.188 — Durable Ben IPC Diagnostic Journal

## Purpose
Fix the diagnostic observability failure where `RUN ISOLATED BEN IPC TEST` could fail/recreate the Activity and return to Ben Model Lab with no stored test result.

## Changes
- Replaced the IPC diagnostic's cross-process SharedPreferences event transport with a bounded append-only file journal in the shared application data directory.
- Critical journal writes are flushed/synced before returning, with an inter-process file lock so main/worker event writers cannot truncate each other.
- Added an atomic active-test marker shared by the main process and `:inference_process`.
- Added stale-run recovery: if the previous IPC run disappears before terminal publication, the next diagnostics surface converts the surviving journal into an explicit `INTERRUPTED` report instead of showing `No test run`.
- Added a dedicated persisted `latest_ipc_report`, preventing later EmbeddingGemma diagnostics from overwriting the IPC result.
- Added `OPEN LAST BEN IPC REPORT` in Test & Diagnostics.
- Added direct `RUN ISOLATED BEN IPC TEST` and `VIEW LAST IPC DIAGNOSTIC` access in Ben Model Lab.
- Complete diagnostics export now prefers the durable IPC report when available.
- Client-side remote-progress events are persisted as heartbeats, so the recovery logic can distinguish a genuinely abandoned run from a slow but active diagnostic.
- Diagnostic test remains model-independent at the transport/lifecycle layer.
- RovexDiagnosticsStore critical writes now use `commit()` for deterministic persistence.

## Stability gate
No claim of compile-green is made here until CI completes successfully. Full source/static/runtime-risk audit is required before device installation.
