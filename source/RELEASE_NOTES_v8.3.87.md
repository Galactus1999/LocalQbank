# Rovex v8.3.87 — Ben Model Integration & Chat UX

- Foreground interactive Ben inference is no longer blocked merely by Android power-save mode when RAM/thermal gates are safe.
- Added direct **Ben Model Lab** for Gemma 3 270M chat and EmbeddingGemma 300M semantic testing.
- Missing neural artifacts are surfaced explicitly in telemetry instead of silently appearing idle.
- Redesigned Dr. Frankenstein screen around the chat/answer area; diagnostics moved to compact live status and Model Lab.
- High-contrast SAVE IMAGE + NOTE → MY NOTES control for dark themes.
- User note images are stored under the Notes image store and no longer counted/listed as Knowledge Vault images.
- Today's solved count scales down further for large numbers to prevent clipping.
- CPU-first neural execution retained; no model weights bundled.
