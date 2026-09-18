# Rovex v8.3.25 — QBank Visibility / Commit-Notification Stability Audit

## Release
- versionName: 8.3.25
- versionCode: 123
- applicationId: com.localqbank.library

## Root-cause correction
The main Home QBank panel could miss a newly imported QBank even though the QBank was already visible in Performance Lab.

Root cause: `QBankDb.importBundle()` called `AppState.changed()` immediately after `db.setTransactionSuccessful()` but **before** `db.endTransaction()`. `MainActivity` refreshes asynchronously in response to that event and could therefore read the pre-commit database snapshot on its separate SQLite connection. Performance Lab, opened later, read the committed database and showed the QBank.

Correction:
- QBank import now ends/commits the SQLite transaction first.
- `AppState.changed("import_completed")` is emitted only after `endTransaction()`.
- Main/Home, Performance, analytics and cache invalidation therefore observe the committed source/test/question state.

## Similar defect search
- Searched all Kotlin sources for `setTransactionSuccessful(); AppState.changed` ordering.
- Found and corrected the same ordering defect in `FlashcardDb.upsertDeck()`.
- `QBankDb.deleteSource()` now emits state invalidation only after its transaction commits and only when a row was actually removed.
- Other transaction-based AppState notifications were reviewed and were already outside their transaction boundaries.

## Data/UI integrity
- QBank source list remains DB-backed and recreated from committed `source` rows.
- Existing `SourceAdapter`/RecyclerView virtualization preserved.
- Performance Lab and Home therefore consume the same committed source of truth.
- No duplicate business-logic manager was introduced.

## Static audit
- XML parsing: PASS
- Per-layout duplicate IDs: PASS
- Dangerous `Thread.sleep`: PASS / none
- `runBlocking`: PASS / none
- `GlobalScope`: PASS / none
- `killProcess`: PASS / none
- broad `catch(Throwable)`: PASS / none
- broad `catch(Error)`: PASS / none
- transaction notification ordering: PASS
- CI version assertions: PASS
- Ed25519 provenance signature: PASS
- AES-GCM provenance payload: PASS
- ZIP integrity: pending final package check

## Compilation
Local Gradle compilation attempted with Gradle 9.3.1 but could not download the distribution because `services.gradle.org` is DNS-unreachable in the current environment. No local APK compilation claim is made. GitHub CI remains authoritative for compilation and APK packaging.
