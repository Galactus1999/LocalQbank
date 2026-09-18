# Rovex v8.3.96 Release Audit

Baseline: v8.3.94 compatibility hold.

Changes are limited to the EmbeddingGemma runtime path, tokenizer artifact handling, Model Lab/Adaptive Engine visibility, dependency wiring, and version/CI assertions.

The Google AI Edge RAG SDK 0.3.0 is a published Android AAR and supplies the local embedding implementation/native runtime. Google’s current EmbeddingGemma model card documents the Android LiteRT variants and separate `sentencepiece.model` tokenizer.

CI remains the authoritative Android compilation gate. Local full Gradle compilation is not claimed if services.gradle.org DNS remains unavailable.
