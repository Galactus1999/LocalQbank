# Rovex v8.3.127 — Adaptive Engine + EmbeddingGemma Diagnostic Audit

## Release identity
- Version: 8.3.127
- versionCode: 225
- Baseline: v8.3.125 Adaptive/Embedding stability compile-fix source
- Application ID: com.localqbank.library

## CI bootstrap correction
- Gradle 9.3.1 is a real published Gradle release.
- CI was failing before Gradle startup because the distribution request to services.gradle.org returned HTTP 504.
- The wrapper distribution URL was changed to the official downloads.gradle.org mirror.
- CI now uses gradle/actions/setup-gradle@v4 with Gradle 9.3.1 and invokes the provisioned `gradle` executable for build/test tasks, while retaining wrapper/version invariants.
- The wrapper script was also changed to use downloads.gradle.org if directly invoked.
- This addresses the observed bootstrap transport failure; it does not claim CI is green until a complete CI run passes.

## EmbeddingGemma full diagnostic
Adaptive Engine → Neural Lab now provides a one-action full diagnostic report. The report automatically collects:
- model presence, size and SHA-256
- official SentencePiece presence, size and SHA-256
- tokenizer parse result and sample token count
- special-token contract and sequence length
- every discovered input/output tensor contract
- selected 768-dimensional FLOAT32 embedding output
- QNN attempted/available/fallback status and failure reason
- CPU/XNNPACK fallback status
- runtime initialization and inference latency
- available RAM before/after
- Java heap and native heap before/after
- thermal status before/after
- power-save state before/after
- finite embedding validation and embedding norm
- related-vs-unrelated semantic smoke test and separation
- final contract/inference/semantic/overall gates
- failure reason when startup or runtime validation fails

The report is retained in BenNeuralTelemetry for the current process and can be viewed/shared from the Adaptive Engine screen. No study data or QBank content is persisted by the diagnostic.

## Failure behavior
- Missing model/tokenizer produces a report rather than a silent null result.
- Governor-blocked execution produces a report.
- Linkage/runtime failures produce a report.
- Deterministic Ben/RAG remains authoritative when neural execution is unavailable.

## Static verification
- XML files: 26
- XML parse errors: 0
- Duplicate IDs within individual layouts: 0
- Architecture regression audit: PASS
- Kotlin CLI parse-oriented scan: no syntax/parse diagnostics found; expected Android/AndroidX/dependency unresolved references remain because the standalone compiler lacks the Android build classpath.
- Existing forbidden-pattern and manager-ownership checks preserved.

## Build status
Android Gradle compilation is not claimed green from this environment. The prior observed CI failure was Gradle distribution bootstrap HTTP 504, not an application compilation error. The corrected source must pass the real GitHub Actions build and emulator tests before release-green status is claimed.


## Additional v8.3.127 audit scope
- EmbeddingGemma execution migrated from legacy `Interpreter`/QNN delegate to LiteRT `CompiledModel` 2.1.5 CPU baseline.
- Direct Qualcomm QNN delegate dependencies removed from the EmbeddingGemma path to eliminate the unresolved `DISPATCH_OP` failure mode observed on SM8650.
- Diagnostic report now includes a flatbuffer marker scan and independently validates SentencePiece parsing even when model initialization fails.
- Actual Android compilation/device validation remains a CI/device gate; no green-build claim is made from local static analysis.

## Verification status
- Architecture regression audit: PASS.
- XML parse: PASS (26 files); duplicate IDs within each layout: 0.
- Kotlin parser-oriented checks: no syntax/brace/parenthesis errors detected in the changed source; standalone compilation remains dependency-incomplete without the Android/Gradle classpath.
- Full Android Gradle compile/CI: NOT VERIFIED in this environment because Gradle distribution/network resolution is unavailable locally. CI remains the authoritative build gate.
