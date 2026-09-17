# Rovex v8.3.6 Release Audit

## Baseline
- App: Rovex / LocalQBank
- applicationId: `com.localqbank.library`
- Baseline: v8.3.5, versionCode 103
- Release: v8.3.6, versionCode 104

## Build-toolchain correction
The v8.3.5 CI failure occurred at `:app:checkDebugAarMetadata` because
`com.github.luben:zstd-jni:1.5.7-16@aar` requires compileSdk 37 while the
project used compileSdk 36.

Changed:
- AGP 8.10.1 -> 9.1.1
- Gradle 8.11.1 -> 9.3.1
- compileSdk 36 -> 37
- targetSdk remains 36
- minSdk remains 26
- External `org.jetbrains.kotlin.android` plugin removed; AGP 9 built-in Kotlin is used.
- Existing Java 17 compile settings retained.
- zstd dependency remains `com.github.luben:zstd-jni:1.5.7-16@aar`.
- Custom `gradlew` launcher pin updated to Gradle 9.3.1.

## CI workflow
- Installs Android API 37.
- Continues using Build Tools 36.0.0.
- Verifies Gradle wrapper is 9.3.1 and launcher pin is 9.3.1.
- Verifies compileSdk 37, targetSdk 36, versionCode 104 and versionName 8.3.6.
- Preserves ARM64 zstd JNI APK assertion:
  `lib/arm64-v8a/libzstd-jni-1.5.7-16.so`
- Duplicate XML IDs are checked only within each individual layout file.
  Reusing an ID across different layouts is allowed.

## Static/source audit performed
- 23 XML resources inspected.
- 51 Kotlin source files inspected.
- `findViewById<T>()` vs XML widget types: PASS.
- Duplicate IDs within individual layouts: PASS.
- Cross-layout ID reuse is not treated as an error.
- Unsafe SQLite PRAGMA via `execSQL`: none detected.
- Native Android text auto-size path: none detected.
- Automatic wrong-answer -> flashcard creation: none detected.
- Thread.sleep/runBlocking/GlobalScope: none detected.
- Workflow YAML parse: PASS.
- Package/applicationId unchanged: `com.localqbank.library`.
- No broad `catch(Throwable)` / fatal Error swallowing pattern detected in app Kotlin sources.

## Compilation status
Local compilation is NOT claimed as verified. The environment cannot resolve
`services.gradle.org`, so Gradle 9.3.1 could not be downloaded. GitHub Actions
must perform the authoritative compilation check.
