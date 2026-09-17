# Rovex v8.3.89 release audit

## Source baseline

- Package: `com.localqbank.library`
- Version: `8.3.89`
- versionCode: `187`
- Baseline: corrected v8.3.87 source, intentionally not the unbuildable v8.3.88 branch.

## Static audit

- XML parse: PASS (26 XML files)
- Per-layout duplicate IDs: PASS
- `runBlocking`: 0
- `Thread.sleep`: 0
- `GlobalScope`: 0
- broad `catch(Throwable)`: 0
- unsafe startup `execSQL("PRAGMA ...")`: 0
- active workflow version checks: PASS
- visual retrieval SQL includes a hard `EXISTS(question_image)` filter: PASS
- persistent Gemma session is bounded by a 2-minute idle timeout: PASS
- no neural model binaries bundled in source: PASS
- ZIP integrity: PASS

## Build status

Actual Android Gradle compilation was attempted with:

`./gradlew :app:compileDebugKotlin --offline --no-daemon`

The environment could not resolve `services.gradle.org` (DNS failure) while downloading the Gradle 9.3.1 distribution. Therefore Android compilation is **UNVERIFIED** locally. CI is the authoritative build gate.

## Research checks

- LiteRT-LM Android Kotlin API and multi-modality/tool capabilities were checked against current Google AI Edge documentation.
- EmbeddingGemma Text Embedder formatting/retrieval guidance was checked against current Google AI Edge documentation and the LiteRT Community model card.
- Current Google Gemma documentation was checked for model modality boundaries; Gemma 3 270M is treated as text-to-text for Rovex, while multimodal image work is reserved for a future appropriate model.
- Current AIIMS INI-CET prospectus information was checked for the current exam structure and marking scheme.

## Product safety boundary

Neural models remain optional accelerators. Deterministic local Ben/Ren/QBank retrieval remains functional when models are missing, blocked, failing, or resource-constrained. No model auto-download or silent external network dependency is introduced.
