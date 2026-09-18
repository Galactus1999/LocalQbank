# Rovex v8.3.21 — Subject Focus Performance & Cross-Engine Stability Audit

Version: 8.3.21  
VersionCode: 119  
ApplicationId: com.localqbank.library

## Changes audited

- Added `SubjectFocusSearchEngine` as a dedicated local retrieval layer.
- Subject Focus now uses bounded SQLite/FTS candidate retrieval instead of scanning and materialising every `QuestionRef` for each query.
- Subject Focus UI search is asynchronous and reports an explicit searching state; obsolete searches are prevented from publishing stale results.
- Search results use the existing progress snapshot for due/wrong/unseen prioritisation.
- Existing `StudyIntelligenceManager` delegates Subject Focus retrieval to the dedicated engine.
- Existing Dr. Frankenstein clinical search no longer performs a full `PerformanceManager.refs()` materialisation solely to map FTS hits back to question references.
- QBank search now accepts a bounded result limit up to 1200 while retaining the existing 400-result default for other callers.
- AppManagers owns the Subject Focus engine and keeps engine ownership centralized.
- No changes were made to QBank business rules, question content, progress semantics, or foreground study policy.

## Stability checks

- XML parsing: PASS
- Duplicate IDs within individual XML layouts: PASS
- `findViewById<T>()` versus XML widget types: PASS
- Forbidden blocking/runtime patterns (`Thread.sleep`, `runBlocking`, `GlobalScope`, `killProcess`): PASS
- Broad `catch(Throwable)` / `catch(Error)`: PASS
- Unsafe SQLite foreign-key PRAGMA: PASS
- Kotlin/source delimiter sanity: PASS
- Workflow version/artifact assertions: PASS
- Provenance Ed25519 signature: PASS
- Encrypted provenance record/version consistency: PASS
- Owner identity absent from `app/src/main`: PASS
- ZIP integrity: PASS

## Compilation limitation

Local Gradle compilation could not be completed because the configured Gradle 9.3.1 distribution cannot be resolved from `services.gradle.org` in the current environment (DNS failure). No claim of local APK compilation is made. GitHub CI remains the authoritative compile/package check.

## Architectural safety

The Subject Focus engine is retrieval infrastructure only. `StudyIntelligenceManager` remains the study-intelligence owner, `FrankensteinSupportEngine` remains the Dr. Frankenstein support layer, `QBankLoadingEngine` remains responsible for QBank navigation/loading, `PerformanceManager` remains the shared cache/progress coordinator, and `RovexBatteryManager` remains a governor/policy layer rather than a business-logic owner.
