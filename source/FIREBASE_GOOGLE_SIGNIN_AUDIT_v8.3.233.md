# Firebase Google Sign-In Forensic Audit — v8.3.233

## Root diagnostic correction

v8.3.232 incorrectly classified any `GetCredentialCancellationException` whose message contained `reauth` or `verification` as `GOOGLE_REAUTH_REQUIRED`. Credential Manager does not define those message strings as a stable error contract. Current Android guidance states that `GetCredentialCancellationException` can represent user cancellation or technical authorization/configuration failure, and unexpected cancellation volume can indicate misconfiguration. The app therefore must preserve the original exception rather than replacing it with a canned reauthentication message.

## v8.3.233 changes
- Removed message-substring `requiresReauth()` classification.
- Removed the misleading `VERIFY GOOGLE ACCOUNT` recovery button and the direct Google Account Security URL helper.
- `GetCredentialCancellationException` is now recorded as `GOOGLE_EXPLICIT_CANCELLATION` with the original exception type/message.
- Sign-in failure UI now exposes a copyable diagnostic containing stage, exception type/message, Play Services status and version.
- `NoCredentialException` remains distinct and explicitly notes that reauthentication is only one possible cause.
- Google explicit Sign-in with Google remains the primary user-initiated flow.
- Version: 8.3.233 / versionCode 327.

## OAuth/signing finding
The checked `google-services.json` contains one Android OAuth client for `com.localqbank.library`, certificate SHA-1 `c3f9f7829c627fb21f15f880753e38fd655e0d7a`, plus the Web client. The source permits local debug signing when CI signing variables are absent, while CI can use the persistent signing configuration. Therefore the installed APK certificate must be measured from the actual APK and compared with Firebase's registered SHA-1.

Firebase's current documentation requires the app SHA-1 for Google Sign-In and specifically calls out release/production SHA-1 for released applications. The CI workflow prints the release APK SHA-1/SHA-256; that fingerprint must be checked against Firebase.

## Validation
- Source ZIP integrity: PASS.
- XML/layout static checks: rerun required after final source change.
- Android Gradle compilation: not locally verifiable if Gradle distribution DNS remains unavailable; do not claim compile-green without CI.
- Device authentication: pending v8.3.233 build/install.
