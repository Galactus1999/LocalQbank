# Rovex v8.3.117 CI Fix Audit

## CI failure
The CI run reached `:app:compileDebugKotlin` and failed only in `HtmlImportActivity.kt:38` because `ViewGroup` was not imported. Kotlin consequently also reported `Cannot infer type for this parameter` and `Unresolved reference 'removeView'` on the same expression.

## Correction
Added `import android.view.ViewGroup` to HtmlImportActivity.kt. No behavior, importer algorithm, database flow, coordinator ownership, or large-file processing path was changed.

## Verification
- Application ID: `com.localqbank.library`
- Version: `8.3.117` / `versionCode 215`
- XML parse and per-layout duplicate-ID scan: PASS
- runBlocking / GlobalScope / Thread.sleep / active catch(Throwable): PASS (none)
- MainActivity direct DB/Prefs: none
- QuizActivity direct DB/Prefs: none
- HtmlImportActivity direct DB/Prefs: none
- Existing CI dependency/signing/zstd/QNN checks preserved

## Local compile
Not reached: Gradle wrapper requires downloading Gradle 9.3.1, but this environment cannot resolve services.gradle.org. CI remains the authoritative compiler/build gate.
