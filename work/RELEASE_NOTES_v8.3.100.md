# Rovex v8.3.100

- Corrected the v8.3.99 EmbeddingGemma direct-LiteRT/tokenizer experiment.
- Restored the Google AI Edge RAG integration already validated as the safer architecture in v8.3.96.
- EmbeddingGemma now uses the official companion `sentencepiece.model`, matching the current Google LiteRT Community model card.
- Removed the misleading `tokenizer.json` path and exposed the actual runtime failure reason in Settings.
- Added embedding dimension sanity checks and retained the 10-second bounded inference wait.
- VersionCode 198 / versionName 8.3.100.
