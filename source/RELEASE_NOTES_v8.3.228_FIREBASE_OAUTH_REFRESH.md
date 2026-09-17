# Rovex v8.3.228 — Firebase Google OAuth Refresh

## Changes
- Replaced `app/google-services.json` with the newly exported Firebase configuration containing both:
  - Android OAuth client for `com.localqbank.library`.
  - Web OAuth client used by Credential Manager via the generated `default_web_client_id` resource.
- Preserved the existing Firebase Authentication, App Check, Firebase AI Logic, Remote Config, and Credential Manager implementation.
- Bumped versionCode 321 → 322 and versionName 8.3.227 → 8.3.228.

## Validation
- Fresh Firebase JSON parsed successfully.
- Android package and OAuth certificate hash match the configured app.
- Web OAuth client is present.
- Existing `GoogleAccountManager` uses `default_web_client_id` through Android resources and `GetGoogleIdOption.setServerClientId(...)`.
- Whole-project ruthless static audit: PASS.
- Persistent release signing SHA-256 CI assertion remains present and unchanged.
- Gradle execution was attempted but could not start because the environment could not resolve `downloads.gradle.org`; therefore Android compilation is NOT claimed here.
