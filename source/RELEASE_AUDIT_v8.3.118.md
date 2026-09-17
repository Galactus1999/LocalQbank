# Rovex v8.3.118 — Stage 4/5 Architecture & Stability Audit

## Release identity
- Application ID: `com.localqbank.library`
- Version: `8.3.118`
- versionCode: `216`
- Baseline: v8.3.117 CI-fix source (versionCode 215)
- Update type: in-place architectural/stability update; package ID unchanged; versionCode monotonic.

## Stage 4 — HtmlImportActivity
Completed the remaining presentation-boundary reduction without replacing `ImportPipelineCoordinator`.

### Changes
- `HtmlImportActivity` reduced from ~1131 lines in the previous baseline to ~488 lines.
- Extracted pure parsing/normalization into `HtmlImportParser`.
- Extracted large-file streaming parser into `LargeHtmlScanner`.
- Extracted WebView extraction JavaScript into `HtmlImportWebExtractor`.
- Activity remains responsible for Android lifecycle/UI, URI selection, progress rendering and orchestration callbacks.
- Database work remains behind `HtmlImportRepository` and import scheduling remains owned by `ImportPipelineCoordinator`.
- WebView lifecycle teardown remains explicit: stop loading, clear history, detach from parent and destroy.
- Existing large-file path remains streaming/staged and does not load the complete large HTML document into memory.
- Existing SAF persisted-URI handling retained.
- WebView popup/multiple-window and mixed-content restrictions retained.

## Stage 5 — SettingsActivity
- `SettingsActivity` reduced from 587 lines to 85 lines.
- New `SettingsScreen` owns presentation construction/event wiring.
- New `SettingsViewModel` exposes a screen-level state seam and delegates mutations through `SettingsRepository`.
- New `SettingsRepository` owns Settings UI preference persistence; it does not replace any application engine.
- Migrated Settings model/tokenizer import from legacy `startActivityForResult/onActivityResult` to AndroidX Activity Result `OpenDocument`.
- Adaptive Engine and Ben controls remain visible and directly controllable from Settings.
- Existing Ben AI safety controls, neural model lab controls, telemetry, knowledge index controls, cognitive switches, and resource-governor visibility were preserved.
- Ben/Adaptive/Performance/Resilience/EngineHealth/EngineMesh/IntelligenceOrchestrator/StudyCore/ImportPipelineCoordinator ownership was not duplicated or replaced.
- The Ben neural live monitor now performs resource snapshot work off the UI thread while UI updates remain on the Activity's main scope.

## MainActivity / QuizActivity
- MainActivity remains presentation/navigation focused and continues to use `MainViewModel`/`MainRepository`.
- Main lifecycle operations for dashboard preparation and backup flush are now surfaced through `MainViewModel`/`MainRepository` rather than being invoked directly from Activity lifecycle methods.
- No direct `QBankDb`, `ProgressStore`, or `SharedPreferences` access remains in MainActivity or QuizActivity.
- No legacy Activity Result API remains in MainActivity, QuizActivity, HtmlImportActivity or SettingsActivity.
- QuizActivity was not subjected to a risky rewrite because its persistence/business ownership boundary was already established; remaining complexity is predominantly UI/WebView/media/session rendering.

## Static safety scan
- `runBlocking`: 0
- `GlobalScope`: 0
- `Thread.sleep`: 0
- active `catch(Throwable)`: 0
- unsafe `PRAGMA foreign_keys=ON`: 0
- target-Activity direct `QBankDb/ProgressStore/SharedPreferences`: 0
- XML files: 26
- XML parse errors: 0
- duplicate IDs within individual layout files: 0
- changed Kotlin source brace-balance: 0 for all changed files
- findViewById audit: 63 checked target references; 0 unknown IDs; the 3 reported `View`/`RovexFlowBorderLayout` cases are valid supertype relationships, not ClassCastException risks.

## Dependency / CI invariants preserved
- Zstandard Android AAR: `com.github.luben:zstd-jni:1.5.7-16@aar`
- SentencePiece: `io.github.eix128:sentencepiece4j:1.0.2`
- QNN: `2.49.0`
- CI asserts `lib/arm64-v8a/libzstd-jni-1.5.7-16.so` in APK.
- Persistent Rovex signing certificate fingerprint validation remains enforced in CI.
- CI version assertions updated to versionCode 216 / versionName 8.3.118.

## Build status
### Local source / structural verification
- Changed-source Kotlin parser scan: no syntax/structural diagnostics such as `expecting`, `type mismatch`, `no value passed`, `cannot access`, or illegal-return diagnostics.
- Standalone Kotlin CLI still reports expected Android/AndroidX unresolved-reference cascades because Android SDK/Gradle dependencies are not supplied to the standalone compiler. The few `ProgressBar.progressTintList` receiver diagnostics are likewise artifacts of compiling Android source without Android stubs.

### Local Android Gradle build
**Not verified.** Gradle bootstrap is blocked by environment DNS resolution:
`curl: (6) Could not resolve host: services.gradle.org`.

### CI
**Not yet green.** This source must pass the existing CI workflow before being called build-green or release-green.

## Architecture rule
The Settings presentation layer is allowed to read/display and invoke authoritative managers, but it does not implement their business logic. `AppManagers` and the existing engine/manager classes remain the authoritative owners.
