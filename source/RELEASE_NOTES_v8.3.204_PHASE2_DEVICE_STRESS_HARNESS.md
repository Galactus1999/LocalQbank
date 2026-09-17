# Rovex v8.3.204 — Ben Phase-2 IPC Stress Hardening

- Baseline: v8.3.203 / v8.3.200 known-good diagnostic lineage.
- VersionCode: 299.
- Production change: fixed a real latest-request-wins terminal-state hole in the isolated inference service.
  A generation superseded before acquiring the native single-flight lock now emits exactly one
  terminal `Cancelled` event instead of silently disappearing.
- Added device-only Phase-2 instrumented coverage for:
  - 10 warm EmbeddingGemma compare requests with latency capture.
  - genuine overlapping A→B latest-request-wins generation churn.
  - cancellation followed by a new native inference request (release-barrier check).
  - isolated inference-process death followed by Binder rebind and fresh inference.
  - repeated trim/rebind transport cycles.
- No claim of Android/CI compilation until an actual build passes.
