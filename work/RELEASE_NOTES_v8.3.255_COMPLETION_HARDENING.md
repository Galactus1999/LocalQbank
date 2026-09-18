# Rovex v8.3.255 — Completion & Error Hardening

## Question AI context
- Added one canonical `BenQuestionAiContextPolicy` for PYQ, PYT, FUTURE RELATED and OTHER OPTIONS modes.
- PYQ mode now consumes only locally explicit PYQ provenance; ordinary related questions are never silently relabelled as PYQs.
- PYT mode carries previous-year/topic/test evidence with provenance caveats.
- FUTURE RELATED mode supplies bounded local pattern evidence and explicitly prevents prediction/leakage claims.
- OTHER OPTIONS mode includes every option, authoritative key and available QBank explanation for distractor-by-distractor analysis.
- The same mode-specific evidence is used by local display and Free AI routing through `ContextPackBuilder`/`FrankensteinContextEngine`.
- Saved Question AI context is now durable, read-back verified, reconstructable from the saved question ID, and actually supplied to the Free AI accelerator when present.

## Frankenstein memory
- Chat memory status now distinguishes recent chat continuity from saved Question AI context.
- Clearing Frankenstein context clears both bounded chat continuity and saved Question AI context after confirmation.
- No API secrets are stored in chat memory.

## Stability / error handling
- Cloud provider generation no longer catches `Throwable`; coroutine cancellation propagates correctly and ordinary provider failures remain eligible for fallback.
- HTTP connections are always disconnected in `finally`.
- Cloud provider endpoints are required to use HTTPS.
- Embedding rerank failure handling no longer swallows `Error`/OOM/LinkageError; cancellation propagates.
- Removed duplicate IME inset application in RenActivity.
- Free AI provider UI was extracted from RenActivity as a UI-only helper, preserving the existing business-logic ownership model and architecture regression limits.

## Provider accuracy
- Groq is labelled as a **Free tier** rather than implying unlimited/free API billing.
- DeepSeek remains explicitly opt-in and is described as paid/low-cost.
- OpenRouter `openrouter/free` remains the free-first router.

## Validation
- Ruthless static audit: PASS.
- Architecture regression audit: PASS.
- Per-layout duplicate-ID audit: PASS.
- Policy source independently compiled with Kotlin compiler using minimal type stubs: PASS.
- Full Android Gradle compilation remains unverified because the environment cannot resolve `downloads.gradle.org`; GitHub Actions remains the authoritative build gate.
