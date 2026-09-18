# Rovex v8.3.236 — Cheap Cloud AI Router Audit

## Scope

This release adds a provider-neutral, free/cheap-first cloud text accelerator while preserving the existing Firebase Gemini web-research path and deterministic Ben authority.

## Provider research (2026-09-17)

- OpenRouter exposes an OpenAI-compatible endpoint and a free-model router (`openrouter/free`). OpenRouter documents 25+ free models and a current free-plan limit of 50 requests/day. The free router supports text/image input and text output and can automatically select a currently available free model.
- Groq exposes an OpenAI-compatible endpoint. Current supported models include OpenAI GPT-OSS 20B/120B and Qwen models, with tool use and structured-output support on the relevant models. Groq publishes organization-level rate limits and API-key authentication.
- DeepSeek exposes an OpenAI-compatible `/chat/completions` API. Current `deepseek-flash` maps to DeepSeek-V4.1-Flash and supports JSON output, tool calls, vision, and a 1M context window. Pricing is usage-based and substantially below premium Gemini pricing for the tested workload.
- Gemini remains the dedicated Firebase AI Logic provider for Google Search/URL grounding. Alternate providers are explicitly labeled as non-web-grounded fallbacks so Ben never fabricates current-search claims.

## Implementation

- Added `BenCloudAiGateway` as the single cloud-text provider boundary.
- Provider order: OpenRouter free → Groq → DeepSeek.
- API keys are encrypted with Android Keystore AES/GCM and are never logged or exported by diagnostics.
- Provider model IDs are locally configurable.
- Each provider has connect/update, official key-page, test, enable/disable controls.
- Cloud provider controls are contained in an Adaptive Engines popup to avoid settings-button congestion.
- Gemini research now permits alternate providers when Google sign-in is unavailable, Gemini is disabled, Gemini is rate-limited, or Gemini returns a quota/availability failure.
- Alternate output is explicitly marked `not web-grounded` and does not create web citation evidence.
- Existing deterministic Ben/QBank/verifier authority is unchanged.
- Existing Firebase App Check and Google Sign-In paths are unchanged.
- Release version bumped to 8.3.236 / versionCode 330.

## Safety / cost controls

- No provider key is hard-coded.
- No provider key is placed in GitHub source or build configuration.
- No automatic provider enrollment or billing activation is performed.
- Fallback is only used after the preceding provider fails; the app does not fan out duplicate requests concurrently.
- OpenRouter response caching is requested where supported to reduce repeated identical request cost.
- Existing Ben local/deterministic path remains available when all cloud providers fail or are disabled.

## Validation

- 28 XML files parsed successfully.
- Duplicate `@+id` audit: no duplicates within individual layout files.
- Kotlin source brace/parenthesis balance checked for changed files.
- Version/stale-version audit performed.
- Gradle Android compilation was attempted but could not start because the environment could not resolve `downloads.gradle.org` (DNS/network limitation). Therefore Android compilation is NOT claimed green; GitHub Actions must provide the authoritative compile/release result.
