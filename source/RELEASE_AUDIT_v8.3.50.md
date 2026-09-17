# Rovex v8.3.50 Stability Audit

- Version: 8.3.50 / versionCode 148
- Baseline: v8.3.47 compile-fix source
- Stability slice 1: monotonic Activity lifecycle epoch prevents stale delayed foreground cleanup.
- Stability slice 2: startup static audit rejects obvious blocking file/database/network primitives inside Activity onCreate.
- Stability slice 3: BenLocalResearchEngine provides an offline-only adapter boundary; no model/runtime/network dependency is added to the stability build.
- Unit tests added for lifecycle epoch and Ben capability contract.
- Existing XML, findViewById, zstd, signing, runtime-risk, backup, note integrity, and architecture audits remain authoritative.
- Local Gradle compilation is not claimed because services.gradle.org is unavailable in the current environment.
