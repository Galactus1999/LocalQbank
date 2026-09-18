# Rovex v8.3.77 Release Audit

- Package: com.localqbank.library
- Version: 8.3.77
- versionCode: 175
- Baseline: v8.3.76 Ben Cognitive Expansion II
- AI model dependency: none added
- Neural runtime startup loading: none
- New persistent layer: BenLocalKnowledgeIndex (lazy SQLite, bounded concept/edge statistics)
- Canonical QBank corpus remains QBankDb + existing FTS4 index
- AppManagers ownership preserved
- QBank source mutation: none
- Network I/O: none introduced

## Research basis
- Android AppSearch is a high-performance on-device structured/full-text search option, but was not added here because Rovex already has a mature local QBank FTS4 path and adding another indexing dependency would increase migration/runtime risk.
- Android Room/SQLite FTS supports full-text search; the existing QBank FTS4 implementation remains the canonical recall layer.
- LiteRT-LM remains optional and was not integrated into this release because engine initialization is potentially long and native-resource-heavy; it must remain background/lifecycle/resource-gated.

## Verification performed in this environment
- ZIP extraction: PASS
- XML parse: PASS
- Per-layout duplicate-ID audit: PASS
- Version/CI identity consistency: PASS
- No broad catch(Throwable): PASS
- Knowledge-index source/wiring audit: PASS
- Gradle Android compilation: UNVERIFIED; Gradle 9.3.1 distribution cannot be downloaded because services.gradle.org DNS is unavailable in this environment.
- Device/runtime validation: requires CI/device execution.
