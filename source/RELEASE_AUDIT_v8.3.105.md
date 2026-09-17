# Rovex v8.3.105 Release Audit

## Root-cause correction
- v8.3.104 device error `IllegalStateException: Cannot copy jni files` was traced to DJL SentencePiece JNI loading/extraction.
- EmbeddingGemma tokenizer execution now uses pure-Java SentencePiece4J 1.0.2; no DJL tokenizer JNI loader is invoked.
- Official local `sentencepiece.model` remains the tokenizer artifact.
- EmbeddingGemma task prefixes and BOS/EOS handling remain explicit.

## Qualcomm execution
- Qualcomm QNN HTP delegate remains enabled for supported Qualcomm SoCs including SM8650.
- JNI libraries use legacy packaging so `nativeLibraryDir` remains usable for QNN/Hexagon loading.
- CPU/XNNPACK fallback remains available when QNN initialization/allocation fails.

## Release invariants
- versionName 8.3.105 / versionCode 203.
- zstd AAR invariant preserved.
- persistent signing workflow preserved.
- Gemma 3 270M LiteRT-LM integration untouched.
- deterministic Ben/Ren fallback preserved.
- AI policy epoch advanced to prevent stale backend failure state from carrying into the corrected runtime.

## Static audit
- XML resources parsed.
- Duplicate IDs checked per individual layout file.
- findViewById<T>() vs XML widget type audit performed.
- Broad catch(Throwable), unsafe PRAGMA, runBlocking, Thread.sleep and GlobalScope scans performed.
- Whole-project source/version/CI consistency scan performed.

## Source artifact
- SHA-256: `48b792fc9eb80772ae26a5d60744224cc43f4c437eac58848f7c0b14a3f89562`

## Verification limitation
Android Gradle compilation is not claimed here unless GitHub CI passes. Device verification on the user's Snapdragon 8 Gen 3 / SM8650 remains the final runtime gate; Adaptive Engine diagnostics must distinguish QNN HTP execution from CPU fallback.
