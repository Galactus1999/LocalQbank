# Rovex v8.3.83 — Ben Grounded Neural Pipeline

- Adds a controlled Ben grounded-neural bridge.
- Retrieves a small bounded set of local QBank evidence before neural drafting.
- Sends the draft back through Ben's deterministic cognitive planner/verifier.
- Deterministic Ben remains the fallback if the model is unavailable, blocked, or rejected.
- Adds an Adaptive Engine test control so the neural path remains explicitly user-visible.
- No automatic model download and no model weights bundled.
- CPU-first, foreground-only, governor-controlled inference remains unchanged.
