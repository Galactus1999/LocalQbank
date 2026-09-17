# Rovex v8.3.103 Deep Code Audit

## Release identity
- versionName: 8.3.103
- versionCode: 200
- applicationId: com.localqbank.library
- Base source: v8.3.101 direct-LiteRT EmbeddingGemma source

## EmbeddingGemma corrections
- Corrected invalid `LongBuffer.allocateDirect` / `IntBuffer.allocateDirect` usage.
- Inputs are passed as native-order direct `ByteBuffer` containing INT64 values.
- Enforced the official 512 model contract used by the LiteRT Community reference: `[1,512]` INT64 `input_ids`, `[1,512]` INT64 `attention_mask`, and `[1,768]` FLOAT32 output.
- Removed host-side mean-pooling and normalization assumptions for the official 512 graph; its output is consumed directly as the 768-D embedding.
- Added explicit tensor-contract diagnostics.
- Added `LinkageError` handling so missing/incompatible native runtime libraries fall back instead of crashing the study app.
- Made Runtime initialization resource-safe if tokenizer/interpreter setup or contract validation fails.
- Kept CPU/XNNPACK 4-thread execution and deterministic fallback.

## CI/source corrections
- Corrected stale CI identity assertion that still expected versionCode 190 / versionName 8.3.92.
- CI now asserts 200 / 8.3.103.
- CI explicitly rejects `LongBuffer.allocateDirect` / `IntBuffer.allocateDirect` and the removed Gecko/LocalAgents route.

## Whole-project audit
- XML parsed: PASS (27 XML files)
- Per-layout duplicate IDs: PASS (0)
- findViewById/XML type audit: PASS (0 mismatches)
- explicit `catch(Throwable)`: PASS (0)
- `runBlocking`: PASS (0)
- `Thread.sleep`: PASS (0)
- `GlobalScope`: PASS (0)
- unsafe PRAGMA scan: PASS
- forbidden GeckoEmbeddingModel in source: PASS (0)
- zstd Android AAR assertion retained: PASS
- ZIP integrity: PASS
- SHA-256: 5b184c2bad7388dab19feeefb9935fad889c7f73b83f86c725fa0a47cd142f68

## Verification boundary
Full Android Gradle compilation was NOT locally verified because services.gradle.org DNS is unavailable in the current environment. CI remains the Android build gate.
