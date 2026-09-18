# Rovex v8.3.111 Release Audit

## CI failure corrected
The uploaded GitHub Actions log for v8.3.110 reached `:app:compileDebugKotlin` and failed only on two Kotlin errors in `QuizActivity.kt`: `examRemainingMs` was exposed through a read-only property alias, but the Activity timer attempted to assign to it.

Corrective change:
- Added `QuizViewModel.setExamRemainingMs(Long)` as the single mutation seam.
- Exam timer initialization, tick, and finish now mutate timer state through the ViewModel.
- No direct Activity mutation of ViewModel-owned session state remains at those sites.

## Release identity
- versionName: 8.3.111
- versionCode: 209
- applicationId: com.localqbank.library
- CI canonical APK badge assertion corrected to versionCode 209.
- Persistent signing certificate assertion preserved.
- zstd ARM64 native-library assertion preserved.

## Static safety checks
- XML parsing: PASS
- duplicate IDs within each individual layout: PASS
- `runBlocking`: none in main source
- `GlobalScope`: none in main source
- `Thread.sleep`: none in main source
- active `catch(Throwable)`: none in main source
- unsafe SQLite PRAGMA pattern: none
- QuizActivity direct QBankDb construction: none
- QuizActivity direct SharedPreferences access: none

## Build status
GitHub Actions for v8.3.110 is confirmed **failed at Kotlin compilation** by the supplied log. v8.3.111 has not been claimed CI-green until its own GitHub Actions run passes. Local Gradle compilation remains environment-blocked when Gradle must resolve `services.gradle.org`.
