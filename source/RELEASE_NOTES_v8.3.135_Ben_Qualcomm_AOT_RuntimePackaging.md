# Rovex v8.3.135 — Ben Qualcomm AOT Runtime Packaging

- Corrects the concrete v8.3.134 finding: Qualcomm AOT runtime libraries were absent from the installed APK native library directory.
- Adds strict CI assertions for Qualcomm SM8650/v75 dispatch/QNN libraries in debug, release, and profile APKs.
- Adds a runtime gate before NPU invocation so a missing dispatch/QNN library is reported explicitly and never reaches an opaque `Failed to invoke the compiled model` call.
- Keeps the official SM8650 Seq512 EmbeddingGemma AOT artifact as the intended NPU model.
- Keeps deterministic Ben/RAG authoritative fallback.
- No launcher artwork or unrelated UI behavior changed.
