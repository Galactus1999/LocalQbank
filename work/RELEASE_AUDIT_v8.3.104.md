# Rovex v8.3.104 Release Audit

## EmbeddingGemma correction
- Manual test no longer remains blocked by a stale failure circuit from an older backend epoch.
- Governor reports the actual blocking reason.
- Qualcomm QNN HTP delegate path is enabled on Qualcomm devices with CPU/XNNPACK fallback.
- QNN dependency versions are pinned to 2.49.0.
- Existing Gemma 3 270M LiteRT-LM path is untouched.
- SentencePiece tokenizer remains local/offline and the official task prefixes are preserved.

## Static stability audit
- 92 Kotlin source files scanned.
- XML resources parsed successfully.
- Duplicate view IDs checked per individual layout file.
- findViewById<T>() checked against XML widget types.
- No broad catch(Throwable).
- No unsafe PRAGMA execSQL.
- No native auto-size typography path.
- No runBlocking / Thread.sleep / GlobalScope in app Kotlin source.
- zstd AAR dependency preserved.
- Persistent signing and existing CI invariants preserved.

## Verification limitation
The exact Android Gradle build could not be executed in this environment because services.gradle.org DNS was unavailable. No claim of CI-green or device-runtime verification is made. The QNN path must be verified on the user's Snapdragon 8 Gen 3 / SM8650 device; logcat should confirm QNN HTP delegation rather than CPU fallback.
