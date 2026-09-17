# Rovex v8.3.20 — Performance / Responsiveness Stability Audit

## Scope
Started from Rovex v8.3.19. Changes are limited to foreground responsiveness, heavy-QBank loading, Dr. Frankenstein workload isolation, and related coordination/audit wiring.

## Architecture changes
- `ExperiencePerformanceManager`: foreground-experience coordinator for expensive derived work; background-priority executor and stale-result generation guard for Home.
- `FrankensteinSupportEngine`: asynchronous local cognition/search, duplicate-request coalescing, bounded 20 s cache, and matching-question ID reuse so a search is not executed twice on the UI thread.
- `QBankLoadingEngine`: one in-flight path per source, bundled section/progress/source metadata cache, background-priority execution.
- `PerformanceManager.lightRefs()`: Home/Dashboard uses a lightweight question index that does not materialize question HTML/text.
- `PerformanceManager.invalidateProgress()`: resume refreshes progress without discarding the expensive question-reference cache.
- Home shell is painted immediately; greeting/theme/search/QBank loading states no longer wait for full analytics/QBank aggregation.

## User-visible fixes
- Home screen shows a usable themed shell immediately while heavy derived data loads in the background.
- Good morning/afternoon/evening greeting is immediate rather than waiting for the full dashboard model.
- Subquestion-bank metadata/progress is loaded off the UI thread and cached.
- Dr. Frankenstein response/search processing is off the UI thread and displays a working state.
- Existing QBank editing and Battery Engine presentation are retained.

## Static audit
- XML parsing: PASS
- Duplicate IDs: PASS; duplicates checked only within each individual XML layout file.
- `findViewById<T>()` vs XML widget type: PASS; no definite mismatch detected.
- No `Thread.sleep`, `runBlocking`, `GlobalScope`, or `killProcess`: PASS
- No broad `catch(Throwable)` / `catch(Error)`: PASS
- No unsafe `PRAGMA foreign_keys=ON`: PASS
- Workflow YAML parse: PASS
- Provenance SHA-256 + Ed25519 signature: PASS
- Modified Kotlin files: no parser-level `expecting` / unexpected-token diagnostics detected; full Android compile remains CI-authoritative.
- ZIP integrity: PASS

## Build limitation
Local Gradle compilation was attempted but Gradle 9.3.1 cannot resolve `services.gradle.org` in the current environment (DNS failure). No claim of local APK compilation is made. GitHub CI remains the authoritative compile/package check.

## Release
- applicationId: `com.localqbank.library`
- versionName: `8.3.20`
- versionCode: `118`
