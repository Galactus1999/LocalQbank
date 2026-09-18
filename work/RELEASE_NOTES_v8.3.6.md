# Rovex v8.3.6

- Fixed the zstd AAR compile-SDK metadata blocker by moving to compileSdk 37.
- Upgraded AGP to 9.1.1 and Gradle to 9.3.1.
- Migrated the app from the external Kotlin Android plugin to AGP 9 built-in Kotlin support.
- Updated the GitHub Actions workflow to install Android API 37 and verify the new toolchain.
- Preserved targetSdk 36 and minSdk 26.
- Preserved `com.github.luben:zstd-jni:1.5.7-16@aar` and the ARM64 native-library APK assertion.
- Corrected the XML duplicate-ID CI audit so IDs are unique only within each individual layout.
- VersionCode increased from 103 to 104.
