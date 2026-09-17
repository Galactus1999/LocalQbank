# Rovex v8.3.2 — Release Audit

## Battery/resource architecture
- Added `RovexBatteryManager` as a central policy layer registered through `AppManagers`.
- It observes battery/charging state, Android power-save mode and thermal status.
- It does not change Android power settings, hold wake locks, kill processes, or own feature/business logic.
- Foreground QBank and flashcard interactions are never blocked by battery policy.
- QBank prefetch/image concurrency and APKG bulk batch sizing can adapt to resource policy.
- Existing specialist engines remain owners of their own execution and data.

## Stability audit
- Preserve package `com.localqbank.library`; update version only.
- XML resource parsing checked.
- `findViewById<T>()` type audit retained.
- Unsafe SQLite PRAGMA audit retained.
- Legacy auto-size crash pattern audit retained.
- Old automatic wrong-answer -> flashcard path audit retained.
- No `onFling` override regression.
- No `Thread.sleep`, `runBlocking`, `GlobalScope`, or `killProcess` additions.
- ZIP integrity checked after packaging.

## Build verification
Local Gradle compilation is environment-limited because Gradle 8.11.1 cannot be downloaded in this runtime. GitHub CI remains the compile gate.
