# Rovex v8.3.43 Release Audit

## Purpose
Correct the v8.3.42 CI resource-linking failure caused by the unsupported raw `android:profileableByShell` manifest attribute.

## Correction
The raw manifest attribute was removed. The `profile` build type retains `isProfileable = true`, allowing AGP to generate the profileable manifest declaration for that variant. Profile configuration is therefore variant-scoped instead of being applied to every APK.

## Identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.43`
- versionCode: `141`
- compileSdk: 37
- targetSdk: 36

## CI gates
- Unit tests
- XML/layout duplicate-ID audit
- findViewById type audit
- startup/runtime-risk audit
- resilience executor audit
- profile build configuration audit
- ARM64 zstd native library assertion
- persistent signing certificate assertion
- package/version identity assertion
- no raw `android:profileableByShell` in app manifest

## Evidence boundary
Local Android compilation is not claimed. GitHub Actions CI is the authoritative build verification environment.
