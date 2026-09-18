# Rovex v8.3.159 — Settings Reorganization Audit

## Scope
- Separate Adaptive Study, Adaptive Engines, Performance & Resources, Ben Safety, Privacy & Security, and Ben Diagnostics into independent Settings routes.
- Remove duplicate Adaptive Engine dashboard from the main Settings index.
- Replace monolithic Adaptive Engine detail presentation with domain-specific dashboard cards, tables, progress bars, and topology/flow infographic.
- Preserve authoritative manager ownership and existing Ben safety policy behavior.

## Navigation contract
- `adaptive_study` → learning signals, study strategy, contextual SRS shadow.
- `adaptive` → Ben edge, cognitive core, neural lab, runtime policy, orchestration.
- `performance` → resources, battery, runtime telemetry, resilience, companion engines.
- `ben_safety` → independent Ben neural safety controls and emergency stop.
- `privacy` → data/capability boundaries; does not open Ben Safety.
- `diagnostics` → telemetry/model diagnostics and Ben Model Lab.

## Static checks
- XML parse: PASS (26/26).
- Duplicate IDs within individual layouts: 0.
- Production `runBlocking`: 0.
- Production `GlobalScope`: 0.
- Production `Thread.sleep`: 0.
- Production `catch(Throwable)`: 0.
- Stale v8.3.158/versionCode 255 references in workflow/build identity checks: 0.
- Architecture audit: PASS with existing large-file warnings.
- Architecture regression audit: pre-existing baseline mismatch (50 singleton/object declarations vs baseline 44); unchanged baseline comparison is not suppressed.
- Full Android/AGP compilation: NOT VERIFIED locally because Gradle distribution host DNS is unavailable (`downloads.gradle.org`). CI remains authoritative.

## Runtime-risk notes
- Settings is presentation-only and does not create competing application-engine owners.
- Detail pages use existing authoritative managers/policies.
- Ben Safety and Privacy are separate screens and separate concepts.
