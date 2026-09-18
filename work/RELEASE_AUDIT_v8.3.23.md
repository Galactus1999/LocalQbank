# Rovex v8.3.23 Performance / Subquestion Loading / Runtime Audit

## User-observed issue investigated
The subquestion-bank screen could show the correct bank/question count while displaying no subsection rows.

## Root cause
`TestListActivity` created a `RecyclerView` but did not assign a `LayoutManager`. RecyclerView therefore had an adapter and data but no layout engine capable of positioning child rows.

## Correction
- Added `LinearLayoutManager` before the adapter is populated.
- Kept the loading adapter and asynchronous QBankLoadingEngine path intact.
- Avoided replacing the existing list with a loading-only layout as part of the data callback path.
- Retained item animator disabled to minimize unnecessary work for large subsection lists.

## Runtime architecture
Added `RovexRuntimeEngine`, an Android-native frame-aware governor inspired by game-engine job scheduling:
- monitors Choreographer frame intervals;
- detects normal/busy/saturated frame pressure;
- responds to Android memory-trim callbacks;
- reduces QBank background worker concurrency from 2 to 1 under frame or memory pressure;
- never interrupts active SQLite work merely to protect animation smoothness;
- does not replace Android's GPU renderer and owns no QBank/study business logic.

This is deliberately a scheduling/streaming/cache pattern rather than introducing an OpenGL/game framework, which would add overhead and crash surface without benefiting ordinary Android text/database UI.

## Static stability checks
- XML parsing: PASS
- Per-layout duplicate IDs: PASS
- RecyclerView layout-manager audit: PASS
- `findViewById<T>()` / XML widget-type audit: PASS
- No Thread.sleep/runBlocking/GlobalScope/killProcess: PASS
- No broad catch(Throwable)/catch(Error): PASS
- No unsafe execSQL(PRAGMA): PASS
- Version/CI assertions: PASS (8.3.23 / 121)
- Provenance Ed25519 signature: PASS
- Provenance AES-GCM decryptability: PASS
- ZIP integrity: PASS

## Compilation limitation
Gradle 9.3.1 compilation was attempted. The environment cannot resolve `services.gradle.org` (DNS failure), so no local APK compilation claim is made. GitHub CI remains authoritative.
