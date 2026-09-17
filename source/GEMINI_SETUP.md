# Ben + Gemini web research setup

Rovex now contains a Firebase AI Logic integration for Gemini and Google-account authentication.
The feature is intentionally optional: without Firebase configuration, Rovex remains offline/local and does not crash.

## 1. Firebase project

Create or use a Firebase project and register the Android app with package ID:

`com.localqbank.library`

Download the generated `google-services.json` and place it at:

`app/google-services.json`

The Google Services Gradle plugin is conditionally applied only when this file exists.

## 2. Google authentication

In Firebase Console:

- Authentication → Sign-in providers → enable Google.
- Register the Android SHA-1/SHA-256 fingerprints used by the build.
- Ensure the generated OAuth web client exists. The Google Services plugin creates `default_web_client_id` from the JSON configuration.

The app uses Android Credential Manager + Google ID token + Firebase Authentication. Gemini requests are blocked until a Firebase-authenticated Google account exists.

## 3. Gemini / AI Logic

In Firebase Console:

- AI Services → AI Logic → Get started.
- Select the Gemini Developer API provider unless a billed Agent Platform/Vertex setup is specifically desired.
- Enable Firebase AI Logic.
- Enforce authenticated-users mode for Gemini requests.
- Enforce Firebase App Check for Firebase AI Logic.

Production should use Play Integrity App Check. Debug builds use the Firebase App Check debug provider so a registered debug token can be used during development.

## 4. Ben + Gemini workflow

`GEMINI WEB` in Dr. Frankenstein is explicitly user initiated.

1. Ben performs local deterministic clinical/QBank interpretation.
2. The local Ben context is passed to Gemini as supporting context only.
3. Gemini uses Google Search grounding for current web evidence.
4. Gemini returns a grounded synthesis plus Google Search source metadata.
5. Rovex displays the final Gemini synthesis and the discovered sources.
6. Local study truth, progress, QBank content, and SRS remain owned by Rovex/Ben deterministic managers.

No Gemini API key is embedded in the APK. Firebase AI Logic is used because its proxy architecture keeps the Gemini Developer API key server-side.

## 5. Privacy boundary

Only the explicitly submitted research question and bounded local context are sent to Gemini. Automatic background cloud research is not enabled.
