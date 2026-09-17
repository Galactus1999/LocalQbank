# Rovex v8.3.92 Release Audit

- Version: 8.3.92 / versionCode 190.
- Baseline: v8.3.91 source.
- Purpose: correct EmbeddingGemma runtime integration after on-device semantic test failure.

## Research cross-check

Google's current MediaPipe Android Text Embedder documentation explicitly supports EmbeddingGemma 300M and documents `TextFormatContext` for task-specific formatting. The Java API exposes `TextEmbedder.TextFormatContext`, `EmbeddingType.RETRIEVAL_QUERY`, `EmbeddingType.RETRIEVAL_DOCUMENT`, `EmbeddingType.SEMANTIC_SIMILARITY`, and `TextRole.DOCUMENT`.

## Source checks

- `TextEmbedder.createFromFile(app, installed.file)`: present.
- Manual `task: ... | query:` / `title: ... | text:` construction in BenEmbeddingGemmaEngine: removed.
- Retrieval query/document contexts: present.
- Semantic similarity context: present.
- CPU-first governor gate: retained.
- Runtime error telemetry includes bounded exception message: present.
- Version 190 / 8.3.92: present in app and CI.
- No model files bundled: CI invariant retained.

## Compilation status

Full Android Gradle compilation must be verified by CI. Local Android Gradle compilation is not claimed unless the actual build reaches and passes compilation/tests.
