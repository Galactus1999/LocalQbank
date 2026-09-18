# Rovex v8.3.125 — Adaptive Engine / EmbeddingGemma / Stability Audit

## Release identity
- Application ID: `com.localqbank.library`
- Version: `8.3.125`
- versionCode: `222`
- Baseline: v8.3.123 Flashcard MVVM deep-corrected source
- Update type: Adaptive Engine presentation redesign + EmbeddingGemma diagnostic/RRF hardening

## 1. Adaptive Engine redesign
Settings → Adaptive Engine was reorganized into a control-room hierarchy:

1. Control Room
   - Adaptive status/autonomy
   - health bar
   - live topology visualization
   - collapsible live runtime telemetry
2. Learning & Strategy
   - learning/confidence/change metrics
   - learning signals
   - current study strategy
   - runtime policy
3. Ben • Cognitive Core
   - cognitive switches
   - specialist/verifier telemetry
   - knowledge index/QBank learning
   - episodic memory and reset controls
4. Neural Lab
   - EmbeddingGemma model + SentencePiece artifact state
   - contract/semantic diagnostic
   - semantic-pair test
   - Gemma 3 270M controls and tests
5. System & Safety
   - hard resource governor
   - battery policy
   - resilience/recovery
   - companion engines
6. Engine Activity
   - recent adaptive activity
   - Frankenstein capability registry
   - safety boundaries

The presentation layer remains presentation/control only. Existing managers and engines remain authoritative.

## 2. EmbeddingGemma direction
Current backend remains direct LiteRT/TFLite execution with pure-Java SentencePiece and optional Qualcomm QNN → CPU/XNNPACK fallback.

The installed generic 512-token model remains a compatibility-hold artifact until real-device execution proves its exact tensor/tokenizer contract. Google documents EmbeddingGemma as a 768-dimensional embedding model with task-specific prompts; retrieval uses `task: search result | query:` for queries and `title: none | text:` for documents. citeturn2search0

A non-destructive `diagnostic()` path now reports:
- installed model/tokenizer presence and sizes
- selected backend
- exact runtime tensor contract
- expected 768-d float output
- semantic-pair cosine score
- elapsed time

The diagnostic runs on `Dispatchers.IO`, does not modify study data, and remains behind the AI resource governor.

## 3. Hybrid RAG improvement
Grounded Ben now uses Reciprocal Rank Fusion (RRF) when semantic reranking succeeds:
- lexical QBank FTS rank remains a first-class signal
- EmbeddingGemma semantic rank becomes a second signal
- RRF combines ranks without fragile cross-model score calibration
- deterministic lexical retrieval remains the fallback when neural execution fails

This follows the strong hybrid-retrieval pattern identified during architecture review, without introducing a second business-logic owner.

## 4. Resource/stability rules preserved
- EmbeddingGemma remains optional and governor-gated.
- Gemma 3 270M remains an optional generation accelerator.
- Neural work remains off the foreground UI thread.
- Deterministic Ben/Ren/QBank cognition remains usable without neural models.
- Adaptive live telemetry is lifecycle-scoped and stops outside `STARTED`.
- No Activity owns QBank/ProgressStore/raw SharedPreferences persistence.
- No new manager/business-logic owner was introduced.

Android guidance supports backing off intensive work under thermal pressure, and newer Android releases expose CPU/GPU headroom APIs that can inform future governor refinement. citeturn0search8turn0search11

## 5. Static audit
- Main Kotlin files: 122
- Instrumented Kotlin files: 1
- XML files: 26
- XML parse errors: 0
- Duplicate IDs within an individual layout: 0
- Generic `findViewById<T>()` references checked: 63
- Unknown referenced IDs: 0
- `runBlocking` in main source: 0
- `GlobalScope`: 0
- `Thread.sleep`: 0
- active broad `catch(Throwable)`: 0
- unsafe SQLite `PRAGMA foreign_keys=ON`: 0
- direct SettingsActivity/SettingsScreen persistence access: 0
- changed Kotlin files pass standalone parser/syntax checks; Android/AndroidX unresolved references in standalone compilation are expected because Android SDK/dependency classpaths are not supplied.

## 6. Build status
Local Android Gradle compilation was attempted:

`./gradlew --offline :app:compileDebugKotlin`

It could not bootstrap Gradle because the environment could not resolve `services.gradle.org` (`curl: (6) Could not resolve host`). Therefore:

- Android compilation: **NOT VERIFIED**
- Instrumentation/device runtime: **NOT VERIFIED in this run**
- CI: **NOT CLAIMED GREEN**

GitHub Actions remains the authoritative build/device gate.

## 7. Packaging/invariants
- Package ID preserved.
- versionCode monotonic: 221 → 222.
- zstd Android AAR invariant preserved.
- SentencePiece dependency preserved.
- Qualcomm QNN dependency/protection preserved.
- persistent signing certificate assertion preserved in CI.
- EmbeddingGemma/Gemma model protections preserved.
- No nested source ZIP added.
