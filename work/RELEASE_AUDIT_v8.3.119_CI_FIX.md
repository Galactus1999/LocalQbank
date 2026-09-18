# Rovex v8.3.119 — CI Failure Correction & Deep Audit

## CI failure analyzed
CI reached `:app:compileDebugKotlin` and reported three root causes:
1. `HtmlImportActivity.kt:368` — `dedupeImportedTests` was left as an Activity-local call after extraction. Corrected to `HtmlImportParser.dedupeImportedTests(out)`.
2. `LargeHtmlScanner.kt:182/216/239` — `EOFException` was used without `java.io.EOFException` import. Added the explicit import.
3. `SettingsScreen.kt:338–537` — a local `val activity = card()` shadowed the enclosing `SettingsActivity`, causing a cascade of `LinearLayout`/`Context` mismatches and unresolved `startActivity`, `recreate`, `openSettingsDocument`, and Toast calls. Renamed the local container to `activityCard` and restored the outer Activity reference.

No importer algorithm, database schema, Ben engine ownership, or manager ownership was changed to fix these compile defects.

## Release identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.119`
- versionCode: `217`

## Static audit
- XML files: 28
- XML parse errors: 0
- duplicate IDs within individual layout files: 0
- `runBlocking`: 0
- `GlobalScope`: 0
- `Thread.sleep`: 0
- active `catch(Throwable)`: 0
- unsafe `PRAGMA foreign_keys=ON`: 0
- target Main/Quiz/Html/Settings Activities use no legacy Activity Result API.
- zstd Android AAR assertion preserved.
- signing certificate validation preserved.
- update-safe versionCode remains strictly above prior release code 215.

Legacy Activity Result calls still present in unrelated `BenModelLabActivity`, `FlashcardActivity`, and `BackupActivity`; they are outside the target Stage 3–5 Activities and were not changed in this corrective build.

## Local build
Attempted `./gradlew --no-daemon :app:compileDebugKotlin --stacktrace`.
Local Gradle bootstrap remains blocked because `services.gradle.org` cannot be resolved in this environment:
`curl: (6) Could not resolve host: services.gradle.org`

Therefore local Android compilation is **NOT VERIFIED**.

## CI status
This source is prepared for the next GitHub CI run. CI must pass before this release is called green.
