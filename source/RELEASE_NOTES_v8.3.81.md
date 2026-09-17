# Rovex 8.3.81 — Ben Tiny Generative Accelerator

- Added optional CPU-first LiteRT-LM backend for Gemma 3 270M.
- Added bounded model artifact validation including LITERTLM magic header validation.
- Added Adaptive Engine controls to install, remove and one-shot test the tiny generative model.
- Neural generation remains explicitly governor-gated and is never loaded during startup.
- Every generation call creates and closes the native engine/conversation; no long-lived native engine is kept resident.
- Model execution is forced onto Dispatchers.IO and uses CPU only in this first integration.
- Three consecutive backend failures trigger the existing circuit breaker.
- No model is bundled in the APK.
- EmbeddingGemma remains a separate retrieval accelerator and is not executed by this release.
