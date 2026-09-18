# Rovex v8.3.130 — Ben / EmbeddingGemma + UI Deep Correction Audit

## Baseline
- v8.3.129 / versionCode 227
- Candidate: v8.3.130 / versionCode 228

## Corrections
1. EmbeddingGemma Qualcomm AOT input handling no longer assumes `createInputBuffers()` must return exactly two buffers. Named `input_ids` / `attention_mask` buffers are resolved first, while one-input AOT variants are accepted only when the exposed token input is identifiable.
2. EmbeddingGemma constructor failures now release native buffers/model/environment handles before propagating the error.
3. Diagnostic input reporting now reflects the actual resolved input contract instead of stopping at the hard-coded two-buffer assertion.
4. Dr. Frankenstein chat applies IME/system-bar insets so the composer remains above the Android 16 keyboard; focused input requests visibility automatically.
5. Adaptive Engine collapsible sections now use theme-adaptive pastel state colors, borders and panel backgrounds so open/closed subsections are visually distinct.
6. Notes + Images save action now derives foreground text from background luminance, preventing dark-on-dark action text during theme transitions.
7. The supplied launcher artwork was intentionally not modified.
8. CI canonical artifact naming was corrected from the stale v8.3.125 name to v8.3.130.

## Required release gates
- Whole-project source scan
- Kotlin/Gradle compilation in CI
- XML parse + duplicate-ID audit within each layout only
- `findViewById<T>()` versus XML type audit
- startup/onCreate runtime-risk audit
- dependency/API signature cross-check
- Qualcomm v75 LiteRT runtime packaging check
- APK signing certificate + version/badging checks
- connected-device EmbeddingGemma diagnostic
- no claim of green status until CI/device tests actually pass

## Local deep-scan results
- Architecture regression audit: PASS — 16 Activities, 44 singleton declarations.
- XML parse / per-layout duplicate-ID audit: PASS — no duplicate IDs within individual layouts.
- `findViewById<T>()` versus XML widget audit: PASS.
- Legacy EmbeddingGemma Interpreter/QNN delegate references: PASS — none found.
- Broad `catch(Throwable)` source pattern: PASS — removed from active source; fatal linkage handling is explicit.
- Historical autosize/PRAGMA/selectAllOnFocus/runtime-risk patterns: PASS.
- Stale active v8.3.129/v8.3.125 release assertions: PASS after correction to v8.3.130 / 228.
- Launcher artwork: intentionally unchanged.
- Kotlin compiler preflight: the local compiler initially exposed one source-local arithmetic type issue in `inferSequenceLength`; it was corrected. A full Android compile could not be completed because the Gradle distribution host `downloads.gradle.org` was DNS-unavailable in this environment. The partial compiler check still requires CI as the authoritative build gate.
