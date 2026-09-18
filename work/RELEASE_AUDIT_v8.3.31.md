# Rovex v8.3.31 — Missed-Reason Collections, Rich Popups & Image+Note Audit

## Scope
Focused follow-up update over v8.3.30. Existing QBank, flashcard, SRS, APKG importer,
Ben/Dr. Frankenstein, performance engines and battery-policy ownership remain unchanged.

## Requested fixes
- Replaced the plain Android "Why did I miss this?" single-choice dialog with a theme-aware rich card dialog containing descriptions and selected-state styling.
- Added Study Tools section "Why I Missed This" with a rich collection popup and question collections for every stored mistake reason plus an all-tagged collection.
- Replaced the main QBank long-press plain item dialog with a theme-aware visual action sheet and themed delete confirmation.
- Fixed Notes → long press → View Question routing by resolving the owning test from questionId before normal testId validation.
- Added question-note image attachments. Long-pressing a rendered QBank image now offers "Save Image" or "Save Image + Note". The latter stores the image in the Knowledge Vault and binds it to the question's Notes record.
- Notes cards render attached images first and the written note beneath them, with bounded bitmap decoding to avoid unnecessary memory pressure.
- Attached image metadata is stored in SQLite with foreign-key cascade; deleting a note also removes its attachment metadata and attached files.
- All newly introduced surfaces use ThemeManager colours; no raw light text is placed on dark surfaces.

## Identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.31`
- versionCode: `129`
- compileSdk: `37`
- targetSdk: `36`
- zstd dependency: `com.github.luben:zstd-jni:1.5.7-16@aar`

## Static/source audit
- XML parse + duplicate IDs within individual layout files: PASS
- Existing findViewById/XML type audit rules retained: PASS
- No Thread.sleep/runBlocking/GlobalScope/killProcess: PASS
- No unsafe `PRAGMA foreign_keys=ON` execSQL path: PASS
- No broad `catch(Throwable)`: PASS
- Persistent CI signing configuration retained and version assertions updated: PASS
- New note-image schema/index and deletion paths present: PASS
- New image+note async path present: PASS
- Why-I-Missed collection path present: PASS
- Rich QBank long-press action sheet present: PASS
- Exact question routing fix present: PASS
- ThemeManager pastel/rich popup palette present: PASS
- Provenance Ed25519 signature verified: PASS

## Runtime-risk audit
- New SQLite table uses `CREATE TABLE IF NOT EXISTS` and index creation only; no destructive migration.
- Attached image loading is bounded to six images per note and uses sampled RGB_565 decoding.
- Image+note saving remains on the existing knowledge-engine background executor.
- Mistake collections use lightweight question refs and existing ProgressStore data; no new per-card database loop is introduced.
- Main QBank long-press UI is local to the existing card and does not change click/resume behavior.
- Notes question routing now resolves `testId` before the existing exact-position path; no schema dependency.
- Existing automatic progress backup continues to capture mistake tags through the existing progress preference map. Image attachment bytes are local derived files and are not silently added to the progress-only backup format.

## Compilation
Local Gradle compilation was attempted but cannot complete because `services.gradle.org` is not resolvable in this environment. GitHub Actions remains the authoritative Android compiler/build environment.

## GitHub CI build-log correction
- Reported Kotlin compilation failure was isolated to four `GradientDrawable.cornerRadius` calls in `QuizActivity.kt` where integer literals were passed to a `Float` parameter.
- Corrected literals to `15f`, `14f`, `20f`, and `14f`; no functional or UI behaviour was otherwise changed.
- This correction is source-level and does not change the version identity: v8.3.31 remains versionCode 129 because the previous v8.3.31 artifact did not successfully compile.
