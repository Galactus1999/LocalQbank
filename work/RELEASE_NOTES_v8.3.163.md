# Rovex v8.3.164 — Diagnostics Runtime/UI Correction

- Fixed one-shot IPC diagnostics attempting to run before the isolated Ben service was bound.
- Added a 90-second diagnostic-specific client/service deadline for first-run EmbeddingGemma initialization.
- Added visible diagnostic progress states and a stop/cancel control.
- Persisted the latest bounded EmbeddingGemma diagnostic in the main process so it survives the isolated-process telemetry boundary.
- Added bounded five-report diagnostic history storage.
- Diagnostics Center is now the canonical testing surface and exposes direct Ben Model Lab access.
- Share export now includes the stored full EmbeddingGemma report plus current main-process telemetry.
- Preserved deterministic fallback and study-data boundaries.
