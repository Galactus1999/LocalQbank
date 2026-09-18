# Rovex v8.3.219 — Gemini / Frankenstein / Student UI Hardening

## Implemented
- Spacious Frankenstein chat surface with reduced control density and larger readable response area.
- Explicit BEN + GEMINI core remains the combined orchestration path.
- Gemini QBank exam-context surface upgraded to a spacious scrollable workspace.
- Added separate PYQ / EXAMS mode using local QBank question/source/test metadata as bounded evidence before Gemini web contextualization.
- Added user-triggered Gemini visual generation using `gemini-3.1-flash-image` and an in-question save-to-My-Notes flow.
- Gemini visuals are explicitly optional and do not replace deterministic QBank truth or Ben verification.
- Performance Lab now has progressive circular metrics, a live 30-sample telemetry trace, compact glance metrics, and actionable <90% parameter cards.
- Weak parameters are sorted by severity and expose an intervention dialog; interventions remain advisory unless an existing governor already authorizes a safety change.
- Persistent Rovex signing certificate fingerprint assertion restored in CI using the established certificate fingerprint.

## Research basis
- Android recommends Material 3 and adaptive layouts for accessible, consistent, responsive Android experiences.
- Firebase AI Logic supports multimodal Gemini requests and Gemini image generation; image-generation models are separate from the general-use Gemini text model.
- Gemini image generation is user-triggered because Firebase documents image-generation models as a distinct capability and notes billing requirements.

## Static validation
- XML parsing: PASS
- Per-layout duplicate-ID audit: PASS
- Ruthless static audit: PASS
- GlobalScope: 0
- Thread.sleep: 0
- TODO/FIXME/NotImplementedError in app source: 0
- Stale gemini-3.7-flash source reference: 0
- CI YAML parse: PASS
- Version: 8.3.219 / versionCode 314

## Build limitation
Android compilation was attempted but the environment cannot resolve `downloads.gradle.org`; therefore no Android compile-green claim is made. GitHub Actions remains authoritative.

## Release-critical note
The CI release job now verifies the actual APK certificate SHA-256 against the established persistent Rovex certificate before accepting the release artifact.
