# Rovex v8.3.126

- Corrected Gradle 9.3.1 CI bootstrap transport path after observed HTTP 504 from services.gradle.org.
- Added a complete Ben / EmbeddingGemma contract diagnostic in Adaptive Engine → Neural Lab.
- Diagnostic automatically captures artifact hashes, tokenizer state, tensor graph contract, dimensions, QNN/CPU backend behavior, latency, RAM/heap, thermal/power state, embedding numerical validity, semantic smoke tests and failure/fallback details.
- Missing artifacts and runtime failures now produce a structured report instead of only a null/Toast failure.
- Added in-app report viewing and sharing.
- Preserved deterministic retrieval and hard AI resource-governor fallback boundaries.
