# Rovex v8.3.5 Release Audit

- Version: 8.3.5 (versionCode 103)
- Application ID: com.localqbank.library
- Primary fix: Android Zstandard native runtime packaging.

## Root cause
The APKG importer used `com.github.luben:zstd-jni:1.5.7-16` as the generic JVM JAR. On Android this can make zstd-jni search for `/linux/aarch64/...`, producing `Unsupported OS/arch` / `ZstdInputStreamNoFinalizer` errors. The zstd-jni project explicitly documents the Android AAR form as `com.github.luben:zstd-jni:VERSION@aar`.

## Fix
Use the Android AAR form and add a CI post-build assertion that the ARM64 native library is actually packaged in the APK.

## Static safety checks
- XML parsing: pass
- Historical unsafe PRAGMA pattern: absent
- onFling regression: absent
- blocking coroutine/global-scope patterns: absent
- automatic wrong-answer -> flashcard API: absent
- zstd dependency: Android AAR form
- CI verifies `lib/arm64-v8a/libzstd-jni-1.5.7-16.so` in the assembled APK

Actual Android compilation/runtime must be validated by CI/device testing.
