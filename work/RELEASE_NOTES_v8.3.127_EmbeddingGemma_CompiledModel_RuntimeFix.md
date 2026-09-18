# Rovex v8.3.127

- Corrected Gradle 9.3.1 CI bootstrap transport path after observed HTTP 504 from services.gradle.org.
- Added a complete Ben / EmbeddingGemma contract diagnostic in Adaptive Engine → Neural Lab.
- Diagnostic automatically captures artifact hashes, tokenizer state, tensor graph contract, dimensions, QNN/CPU backend behavior, latency, RAM/heap, thermal/power state, embedding numerical validity, semantic smoke tests and failure/fallback details.
- Missing artifacts and runtime failures now produce a structured report instead of only a null/Toast failure.
- Added in-app report viewing and sharing.
- Preserved deterministic retrieval and hard AI resource-governor fallback boundaries.


## v8.3.127 runtime correction
- Replaced the legacy LiteRT 1.4.2 `Interpreter` + Qualcomm QNN delegate EmbeddingGemma path with LiteRT 2.1.5 `CompiledModel` CPU execution.
- Removed direct `qnn-runtime` / `qnn-litert-delegate` dependencies from this EmbeddingGemma baseline.
- Added graph-byte diagnostics for `DISPATCH_OP`, LiteRtDispatch, Qualcomm, and known Snapdragon model markers.
- Corrected diagnostic failure reports so tokenizer parsing is reported independently of model-runtime initialization.
- Preserved deterministic Ben/RAG fallback and kept Qualcomm/NPU execution disabled until the exact dispatch runtime is independently validated.
- VersionCode advanced to 225; package ID remains `com.localqbank.library`.


## Build status
The source has passed static/architecture/XML checks. A green Android build is not claimed until GitHub CI actually completes.
