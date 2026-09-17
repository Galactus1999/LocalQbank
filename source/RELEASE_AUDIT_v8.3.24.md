# Rovex v8.3.24 — Scroll/Runtime Performance Audit

## Requested fixes
- Main QBank library now uses a RecyclerView instead of a nested ScrollView/LinearLayout list.
- Main QBank RecyclerView arbitrates touch interception so vertical gestures inside the QBank list remain with the list rather than scrolling the whole dashboard.
- Bookmark collection retrieval now resolves only targeted stable keys instead of materializing the complete QuestionRef index.
- Collection refreshes are coalesced so onCreate/onResume cannot start duplicate heavy loads.
- Custom-test pools use lightweight question references where full question text is unnecessary.
- Exam-mode sampling uses the lightweight index.
- Subject Focus / Frankenstein paths remain unchanged and are not replaced by a competing engine.
- RovexRuntimeEngine is integrated with major RecyclerViews and the QBank/experience worker budgets.
- RovexRuntimeEngine now provides conservative hardware-renderer configuration for the lightning Canvas surface; Android remains responsible for GPU scheduling.

## Scroll audit
- Main dashboard QBank list: independent RecyclerView with touch interception arbitration — PASS.
- Subquestion-bank list: RecyclerView + LinearLayoutManager — PASS.
- Bookmark collection: RecyclerView — PASS.
- Search results: RecyclerView — PASS.
- Flashcard deck library: single outer ScrollView; no nested vertical scrolling child, so the main-QBank nested-scroll defect does not reproduce there. — PASS.
- Custom-test selection sheets: single ScrollView per dialog; no nested vertical RecyclerView/ScrollView. — PASS.

## Static safety checks
- XML parsing — PASS
- Per-layout duplicate IDs — PASS
- `findViewById<T>()` vs XML widget type scan — PASS
- No `Thread.sleep` / `runBlocking` / `GlobalScope` / `killProcess` — PASS
- No broad `catch(Throwable)` / `catch(Error)` — PASS
- No unsafe `execSQL("PRAGMA ...")` — PASS
- Provenance Ed25519 verification — PASS
- Provenance SHA-256 consistency — PASS
- Release version/CI assertions updated to 8.3.24 / versionCode 122 — PASS
- ZIP integrity — PASS

## Build limitation
Gradle 9.3.1 could not be downloaded in the current environment because `services.gradle.org` DNS resolution is unavailable. Local APK compilation is therefore not claimed as verified. GitHub CI remains authoritative for final compilation/package verification.
