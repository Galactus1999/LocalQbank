# Rovex v8.3.106 — Release Audit

## Identity
- versionName: 8.3.106
- versionCode: 204
- Baseline: v8.3.105 / versionCode 203

## CI failure corrected
GitHub CI for v8.3.105 reached Kotlin compilation and failed at:
`BenEmbeddingGemmaEngine.kt:268:33 Unresolved reference 'processor'`.

Root cause: SentencePiece4J 1.0.2 exposes tokenization through `Model.encodeNormalized(text, SentencePieceAlgorithm)`, not `Model.processor.encode(text)`.

Correction:
- Replaced the invalid `tokenizer.processor.encode(text)` call with `tokenizer.encodeNormalized(text, tokenizerAlgorithm).toIntArray()`.
- Kept the pure-Java SentencePiece4J path; no DJL SentencePiece JNI loader is used.

## EmbeddingGemma architecture preserved
- User-imported Qualcomm SM8650 EmbeddingGemma remains the primary accelerator target.
- Qualcomm QNN HTP path remains optional and governor-controlled.
- CPU/XNNPACK remains fallback.
- Deterministic Ben/Ren retrieval remains authoritative.
- Gemma 3 270M integration remains unchanged.

## Static audit
- XML parse: PASS (26 XML files)
- Duplicate IDs per individual layout: PASS
- No active `catch(Throwable)` found in app source
- No active `GlobalScope` found
- No active `runBlocking` found
- No active `Thread.sleep` found
- No unsafe `PRAGMA foreign_keys=ON` found
- No remaining `tokenizer.processor.encode` reference
- Version/CI assertions advanced to 8.3.106 / 204
- ZIP integrity: PASS

## Build status
The provided v8.3.105 GitHub log is a Kotlin compile failure, not a runtime failure. Local Gradle compilation remains unavailable in this environment because `services.gradle.org` DNS resolution is blocked. Therefore v8.3.106 is **not claimed CI-green** until GitHub Actions passes.
