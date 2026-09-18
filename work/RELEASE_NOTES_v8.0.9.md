# Rovex v8.0.9 — CI Compile Fix / Release Audit

## CI root causes fixed
- `FlashcardActivity`: explicit `Runnable` + delay passed to `View.postDelayed(...)`; avoids Kotlin overload resolution failure.
- `FlashcardStudyActivity`: restored the missing `restartWithLimit(Int)` action used by the reviewer menu.
- `HtmlImportActivity`: added the missing `GradientDrawable` import used by section-progress rows.
- `StudyToolsActivity`: corrected the Dialog close-button context receiver to `this@StudyToolsActivity`.

## CI/toolchain hardening
- Version: 8.0.9 / versionCode 79.
- AGP 8.10.1, Gradle wrapper 8.11.1, JDK 17, compileSdk/targetSdk 36 retained.
- CI now uses `./gradlew` explicitly for the APK build and prints `./gradlew --version` before compilation, preventing a runner-installed Gradle version from becoming the build executable.

## Ren search refinement
- Ranking now strongly prioritises matches in the actual question stem over test/source metadata.
- Exact clinical phrase matches receive a strong ranking boost.
- Expanded cancer-marker variants include tumor/tumour/neoplasm/malignancy marker and biomarker forms.
- Generic metadata matches are deliberately down-weighted to reduce unrelated results such as paediatric imaging questions.

## Release verification
- XML layout parsing: passed.
- `findViewById<T>()` vs XML widget inheritance audit: 53 calls checked, no mismatches.
- Resource/manifest checks: passed after accounting for values-defined IDs and XML resources.
- Missing Dialog/AlertDialog imports: passed.
- Invalid `singleLine =` property audit: passed.
- Blocking/risky primitive audit (`Thread.sleep`, `runBlocking`, `GlobalScope`, `killProcess`): passed.
- Activity startup/window-policy audit: passed.
- Kotlin changed-file delimiter/syntax sanity: passed; standalone `kotlinc` could not provide Android compilation because Android/Gradle classpaths are not available in this environment.

## Verification limitation
The uploaded CI log shows the previous v8.0.8 build reached `:app:compileDebugKotlin` and failed on five Kotlin errors. The environment used here cannot download Gradle 8.11.1 from `services.gradle.org`, so the corrected v8.0.9 APK cannot be claimed as locally compiled. GitHub Actions must verify the next build.
