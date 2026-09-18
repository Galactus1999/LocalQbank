# Rovex v8.3.218 — Gemini + Frankenstein Core / QBank Context / Performance Lab

## Implemented

- Google sign-in now follows the current Android Credential Manager flow: authorized-account automatic sign-in first, then device account selection when no authorized credential exists.
- Added secure nonce generation and clearer Firebase/OAuth failure diagnostics.
- Added a visible Google account / Gemini setup button in Dr. Frankenstein.
- Added a guided Firebase setup path when `google-services.json` / Firebase Google OAuth configuration is absent. The app does not fabricate Firebase project credentials.
- Added the primary **BEN + GEMINI CORE** action to combine local Ben reasoning with Gemini web research and final Ben verification.
- Added **GEMINI + BEN • EXAM CONTEXT** directly to every QBank question. It sends the exact question/options/keyed answer/local explanation as explicit user-requested context and returns exam-focused concepts, distractor discrimination, high-yield associations, traps and memory hooks with grounding when available.
- Reworked Performance Lab around animated circular metrics with tap-to-explain dialogs.
- Added a visible **Parameters < 90%** attention section so weak runtime/adaptive signals are surfaced instead of hidden in bars.
- Preserved deterministic local QBank/study authority; Gemini remains an external research collaborator and does not mutate question truth, progress or schema.

## Validation

- `tools/ruthless_audit.sh`: PASS.
- XML parsing and per-layout duplicate-ID audit: PASS.
- Gradle Android compilation was attempted but could not start because this environment cannot resolve `downloads.gradle.org` DNS. Therefore this source is **not claimed CI-green** until GitHub CI or another working Gradle environment compiles it.

## External implementation basis

- Firebase's current Android Google Sign-In guidance recommends Credential Manager, authorized-account auto sign-in, and fallback to all device accounts when no authorized credential exists.
- Firebase AI Logic supports Gemini Search grounding and URL context; grounding metadata provides source chunks and support indices used for source alignment.
- Firebase AI Logic's current setup flow requires Firebase project/app configuration and recommends App Check for production AI access.
