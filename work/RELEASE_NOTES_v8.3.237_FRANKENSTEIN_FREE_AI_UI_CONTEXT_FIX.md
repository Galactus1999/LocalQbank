# Rovex v8.3.239 — Frankenstein Free AI / Context / UI Hardening

## Scope
- Removes Gemini from the normal Ben/Frankenstein provider and question-context UI paths.
- Adds a dedicated **FREE AI** menu inside Dr. Frankenstein for user-configured OpenRouter Free and Groq Free providers; DeepSeek remains optional low-cost.
- Replaces the question-solving **BEN + GEMINI** context action with **BEN + AI • EXAM CONTEXT** and a three-way flow: Ben Local, Free AI, Web Search.
- Adds persistent Exam Profile: NEET-PG, INI-CET, FMGE, General Medical.
- Propagates Exam Profile into Frankenstein local context, EmbeddingGemma semantic query context, Gemma 3 270M grounded prompts, and cloud prompts.
- Documents the actual grounded neural path in Adaptive Engine: QBank/FTS → EmbeddingGemma → RRF/contrastive evidence → Gemma 3 270M → deterministic verifier.
- Removes the Home Good morning/afternoon/evening hero panel completely.
- Reuses the saved colour-flow greeting colour on the Performance Lab front card and adds **CONTINUE STUDY** there.
- Removes the redundant Study & Learning item from Settings.
- Removes small descriptive subtitles from Settings category/card headings to reduce visual repetition.
- Adds colour-coded borders/icon surfaces to Settings categories.
- Hardens the QBank Next button with an explicit transition guard, disabled-state feedback, and queued UI transition.

## Stability invariants
- Deterministic QBank truth remains authoritative.
- Ben neural inference remains optional and resource-governed.
- Existing isolated inference process, cancellation/latest-request-wins, thermal/resource governor, verifier, SRS, backups, signing, Firebase CI and zstd requirements are not intentionally changed.
- No API keys are stored in source; user keys remain Android-Keystore protected.

## Verification performed
- Source-wide Gemini reference scan after UI/provider removal.
- XML parsing and duplicate-ID audit.
- Kotlin changed-file syntax/static audit.
- Version/CI stale-reference audit.
- ZIP integrity verification.
- Android Gradle compilation is only reported green if an actual build passes; otherwise CI remains the authoritative gate.
