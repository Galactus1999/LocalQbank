# Rovex 8.3.223 — Frankenstein Context + Gemini UX Architecture

## Implemented
- Added `FrankensteinContextEngine` as a bounded retrieval/context assembly layer.
- Context combines local QBank related questions, recorded exam/source provenance, learner concept mastery, and question-linked notes.
- Explicitly prevents the Gemini prompt from inventing PYQ status, exam names, years, recurrence, or frequencies not established by local provenance.
- Gemini question research now receives the Frankenstein Context envelope before web research.
- Gemini explanatory research also receives the same bounded local context.
- Added a spacious Frankenstein Context card to the chat workspace with `INSPECT`, `LOCAL`, and `WEAKNESS` actions.
- Context/weakness inspection runs off the UI thread.
- Google/Firebase setup wording was simplified for non-technical users; no Gemini API key is requested or embedded.
- Existing Firebase AI Logic remains the secure mobile Gemini transport; Firebase is not the owner of Ben's local intelligence or study state.
- Version 8.3.223 / versionCode 317.

## Architecture
`Ben Cognitive Core -> Frankenstein Context -> optional Gemini provider -> Ben verifier -> response composer`

The local QBank, progress, SRS and notes remain authoritative. Gemini is an optional research collaborator.

## Validation
- `tools/ruthless_audit.sh`: PASS
- XML parsing: PASS
- Per-layout duplicate-ID check: PASS
- Android Gradle compilation: NOT CERTIFIED in this environment because Gradle distribution DNS/download access is unavailable. CI must remain the final compilation authority.
