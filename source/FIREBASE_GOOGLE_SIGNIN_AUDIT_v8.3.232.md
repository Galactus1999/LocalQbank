# Rovex Firebase / Google Sign-In Audit — v8.3.232

## Current observed failure
The device reaches the Google account chooser, then Google/Android Credential Manager reports that the selected account requires re-authentication. This proves the app is reaching the Google credential provider; the failure is occurring in the account authorization/re-authentication stage before Firebase Auth receives a Google ID token.

## Source findings
1. `google-services.json` is current and matches package `com.localqbank.library`.
2. The JSON contains both the Android OAuth client and Web OAuth client. The generated `default_web_client_id` therefore comes from the Web client, as required by Firebase/Android Credential Manager.
3. The app was using Credential Manager 1.3.0 + googleid 1.1.1.
4. The previous flow tried the bottom-sheet `GetGoogleIdOption` path first. Google documents that this path excludes accounts requiring re-authentication; the explicit `GetSignInWithGoogleOption` path is the documented path for such accounts.
5. The previous implementation reused one nonce across fallback requests. v8.3.232 generates a fresh cryptographically random nonce for every credential request.
6. Error handling was too broad: checking whether an arbitrary error message contained the string `10` could misclassify unrelated errors as `DEVELOPER_ERROR`. v8.3.232 removes this false-positive behavior.
7. Sign-out now clears both FirebaseAuth state and Credential Manager provider state.
8. Firebase AI Logic now requests limited-use App Check tokens, preparing the app for replay protection and the mandatory AI Logic App Check enforcement announced for November 2, 2026.

## v8.3.232 authentication flow
User taps G -> explicit Sign in with Google -> Credential Manager -> Google account verification/re-authentication if required -> Google ID token -> FirebaseAuth.signInWithCredential -> authenticated Firebase user.

If the explicit flow is interrupted, the app does not loop. If it reports a genuine re-authentication requirement, the UI offers a Google Account Security action. The standard Credential Manager account-sheet flow remains a bounded recovery path.

## Firebase / Google Cloud requirements to verify
- Firebase Authentication: Google provider enabled.
- Android Firebase app package exactly `com.localqbank.library`.
- SHA-1 for every signing certificate that will produce an installed APK, especially the persistent CI/release certificate. Firebase requires SHA-1 for Google Sign-In.
- SHA-256 for the production signing certificate for Firebase App Check / Play Integrity.
- Updated `google-services.json` after OAuth setup.
- Android OAuth client and Web OAuth client both present.
- Credential Manager uses the Web OAuth client as `serverClientId`.
- Google account exists on the device and is not blocked by device/account security state.
- Google Play services / Credential Manager provider must be available and current.
- Do not use an embedded WebView for Google authentication.
- Google Auth Platform audience/branding configuration must be valid for production; it is not itself the cause of the observed Credential Manager re-authentication error.

## App Check / Firebase AI Logic production requirement
Firebase currently states that App Check enforcement for Firebase AI Logic becomes mandatory on November 2, 2026. Rovex now uses Play Integrity for release builds and the debug provider for debug builds. The Firebase console must register the production app with its release SHA-256. Debug builds must have their App Check debug token allow-listed if AI Logic App Check enforcement is active.

For production distribution outside Google Play, configure Play Integrity/App Check according to the documented distribution-channel settings; do not assume the Play-recognized/licensed verdicts apply to sideloaded builds.

## Remaining external configuration item
The source can prove the package and Android/Web OAuth clients, but it cannot know the Firebase console's stored SHA-1 for the CI signing key. CI now prints the release APK SHA-1 and SHA-256 so the release SHA-1 can be compared directly with Firebase Console -> Project settings -> Android app -> SHA certificate fingerprints. A SHA-256 registration alone is not sufficient for Firebase Google Sign-In.

## Validation status
- XML parsing: PASS
- Per-layout duplicate-ID audit: PASS
- Firebase JSON Android/Web OAuth structural audit: PASS
- Version: 8.3.232 / versionCode 326
- Android Gradle compile: NOT VERIFIED locally because Gradle distribution DNS resolution is unavailable in this environment.
- Device authentication: NOT YET VERIFIED for v8.3.232.
