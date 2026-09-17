# Rovex v8.3.106 — EmbeddingGemma tokenizer compile correction

- Corrected SentencePiece4J API usage: `Model.encodeNormalized(text, algorithm)` replaces the nonexistent `tokenizer.processor.encode(text)` call.
- Preserved pure-Java/offline SentencePiece tokenization; no DJL JNI loader.
- Advanced release identity to versionCode 204 / versionName 8.3.106.
- Advanced Ben AI runtime policy epoch so the corrected tokenizer/runtime path cannot inherit the previous release epoch.
- Qualcomm SM8650/QNN and CPU/XNNPACK fallback architecture preserved.
