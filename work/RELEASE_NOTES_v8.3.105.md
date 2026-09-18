# Rovex v8.3.105 — EmbeddingGemma Android tokenizer/QNN correction

## Root-cause correction
- The v8.3.104 runtime failure `IllegalStateException: Cannot copy jni files` was traced to DJL SentencePiece JNI initialization, before EmbeddingGemma inference.
- Removed the DJL SentencePiece Android JNI dependency from the EmbeddingGemma execution path.
- Added pure-Java SentencePiece4J 1.0.2 for local/offline tokenization, eliminating native tokenizer extraction/copying.
- Preserved the official local `sentencepiece.model` artifact and EmbeddingGemma task prefixes.

## Qualcomm path
- Preserved Qualcomm QNN HTP execution for Snapdragon/SM8650.
- Enabled legacy JNI packaging so QNN native/Hexagon libraries remain available from `applicationInfo.nativeLibraryDir`.
- CPU/XNNPACK remains a safe fallback if QNN initialization/allocation fails.

## Safety
- Updated Ben AI policy epoch so failures from the previous EmbeddingGemma backend do not poison the corrected tokenizer/runtime.
- Gemma 3 270M generation path remains unchanged.
- Deterministic retrieval remains authoritative and is used on any neural failure.

## Verification status
- Static/source audit required before release.
- Android build and Snapdragon 8 Gen 3 device execution must be verified by GitHub CI/device testing.
- No claim of CI-green is made until the actual workflow passes.
