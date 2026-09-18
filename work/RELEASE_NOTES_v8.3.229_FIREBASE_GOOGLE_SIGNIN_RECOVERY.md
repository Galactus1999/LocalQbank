# Rovex v8.3.229 — Firebase Google Sign-In Recovery

- Fixed the Google sign-in recovery path after Credential Manager returned `NoCredentialException` for both authorized-account and all-account Google ID requests.
- Added the documented explicit `GetSignInWithGoogleOption` flow as the final recovery path for the explicit Google sign-in button.
- Preserved the existing `default_web_client_id` / Web OAuth client and secure nonce flow.
- Preserved Firebase Authentication integration and deterministic/local Ben fallback.
- Bumped versionCode 322 → 323 and versionName 8.3.228 → 8.3.229.

## Validation
- XML parsing: PASS
- Per-layout duplicate-ID audit: PASS
- Ruthless production scan (GlobalScope / Thread.sleep / production runBlocking / TODO/FIXME / NotImplementedError): PASS
- Signing fingerprint CI assertion preserved.
- Android Gradle compilation: NOT VERIFIED in this environment because `downloads.gradle.org` DNS resolution is unavailable.
