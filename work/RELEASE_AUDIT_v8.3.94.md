# Rovex v8.3.94 Release Audit

- Baseline: v8.3.93 source.
- Version: 8.3.94 / versionCode 192.
- v8.3.93 CI failure cause: tasks-text 0.10.36 is not published/resolvable in the Google Maven artifact set used by CI; stable tasks-text currently resolves through 0.10.35.
- This release deliberately fails closed for EmbeddingGemma rather than compiling against unavailable APIs or repeatedly triggering runtime failures.
- Deterministic retrieval remains the authoritative fallback.
- Full Android Gradle compilation is CI-gated; local compilation is not claimed.
