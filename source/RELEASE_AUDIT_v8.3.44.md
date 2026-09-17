# Rovex v8.3.44 Release Audit

## Purpose
Make the CI-produced user APK a true in-place update artifact so users do not need to uninstall Rovex between builds.

## Update invariants
- applicationId remains `com.localqbank.library`.
- versionCode advanced monotonically to `142`.
- versionName is `8.3.44`.
- CI uses the persistent Rovex signing keystore for debug, profile, and release variants.
- The canonical user artifact is the signed `release` APK, not the debug/profile APK.
- CI verifies the canonical release APK certificate against the pinned Rovex SHA-256 certificate.
- CI verifies the canonical release APK package name and version metadata.
- CI verifies ARM64 `libzstd-jni-1.5.7-16.so` is present.
- CI uploads `Rovex-UPDATE-v8.3.44` as the artifact to install over the existing app.

## Android update behavior
Android accepts an APK as an update when the package identity matches, the signing identity is compatible, and the new version code is forward. The application data is retained during a normal update; uninstall is not part of the release workflow.

## Safety
- No application business logic was changed in this release.
- Existing unit-test and static/runtime-risk gates remain mandatory.
- The profileable build remains available for future Macrobenchmark work but is not the canonical user-install artifact.
