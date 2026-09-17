# Rovex v8.3.36 — Incremental Refactor Slice Audit

Version: 8.3.36
versionCode: 134
applicationId: com.localqbank.library

## Scope

This release is a small strangler-fig refactoring slice. It extracts TestList row presentation-state calculation from the RecyclerView adapter into an Android-free Kotlin component. No layout XML, colors, styles, view IDs, database schema, or authoritative manager ownership was changed.

## Changes

- Added `TestListPresentation` and `TestRowPresentation`.
- Moved sub-QBank title/subtitle/progress-state calculation into the pure component.
- Added four deterministic unit tests for not-started, in-progress, completed, and empty tests.
- Preserved existing `TestAdapter` rendering, click listeners, long-press edit, and theme/color logic.
- CI version/test-gate checks advanced to 8.3.36 / 134.

## Verification performed in this environment

- Source ZIP SHA-256 verified before extraction against the supplied v8.3.35 artifact: 3b4778ccc30c108033016024cd5c1252ed801ac4d9b5954e1fa862dba0272bfe
- XML parsing audit: PASS
- Duplicate IDs within each individual layout: PASS
- `findViewById<T>()` vs XML widget type audit: PASS
- Forbidden blocking/process-kill scan: PASS
- Package/version/Zstandard CI invariants: PASS
- Pure extracted production slice compilation with `kotlinc`: PASS
- Extracted/pure regression tests: 22/22 PASS (run with a local lightweight JUnit assertion harness because the Android Gradle dependency graph is not available locally)
- Private signing/provenance material search: none found in source tree

## Not claimed

A complete Android Gradle build was not performed. The repository wrapper points to Gradle 9.3.1 at services.gradle.org, but that distribution is unavailable in the current environment because external Gradle distribution resolution is unavailable. GitHub Actions remains authoritative for the Android build, APK signing, certificate fingerprint, and ARM64 zstd verification.

## Safety decision

This release intentionally avoids ViewModel/StateFlow and Hilt in this slice. Those changes are deferred until additional pure-logic and repository seams provide a stronger safety net. No XML/resources were touched.
