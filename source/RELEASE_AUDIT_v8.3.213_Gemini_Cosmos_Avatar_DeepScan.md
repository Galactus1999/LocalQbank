# Rovex v8.3.213 — Deep Scan / Cosmos / Avatar / Gemini

## Baseline
- v8.3.212 Phase-3 production-hardening source.
- versionCode 308 / versionName 8.3.213.
- Package ID unchanged: `com.localqbank.library`.

## Compiler/source findings
1. Fixed the invalid Kotlin expression `AnimationPolicy.class != null` in `FlashcardAnimationPolicyTest.kt` → `AnimationPolicy::class.java != null`.
2. Re-ran source-level syntax-oriented compilation checks on changed Kotlin files. No parser/syntax errors were reported. Full Android compilation could not be completed because the environment cannot resolve/download the Gradle distribution from `downloads.gradle.org`.
3. No `GlobalScope`, `Thread.sleep(`, TODO/FIXME markers, `NotImplementedError`, or per-layout duplicate XML IDs remain in the scanned source.
4. XML parsing: 0 errors.

## Architectural findings / decisions
- Ben's existing AppManagers remain authoritative. Gemini is a collaborator/gateway, not a second study-state owner.
- Gemini initialization is lazy; it is not initialized during normal application startup.
- No Gemini API key is embedded in source or APK code. Firebase AI Logic is used as the gateway.
- Google authentication is explicit and user initiated through Credential Manager + Firebase Authentication.
- Gemini cloud research is not automatic/background work.
- Local Ben/QBank retrieval remains available when Firebase/Gemini is unavailable.
- App Check is wired for production Play Integrity and debug App Check. Firebase configuration is optional until `app/google-services.json` is supplied.
- Google Search grounding metadata is preserved; when Google's rendered Search entry point is returned, it is rendered in a non-JavaScript WebView.
- Existing `android:usesCleartextTraffic="true"` remains a compatibility/security debt because Rovex historically accepts HTTP image/content URLs. It was not silently changed because doing so could break imported QBanks. A future network-security tightening should be tested against the full WebView/import corpus.

## Themes
- Added `Cosmos • Galaxy`: AMOLED-black base with procedural stars, nebula fields, planets and purple/blue celestial accents.
- Added `Pandora • Avatar`: movie-inspired/Pandora-inspired palette with deep blue/indigo surfaces, cyan bioluminescence and celestial accents. No copyrighted movie artwork or assets are bundled.
- Themes are selectable from Settings and the Home quick theme picker.
- Procedural background rendering is static/lightweight; it does not add an animation loop or neural workload.

## Gemini workflow
1. User taps `GEMINI WEB`.
2. If needed, Google account sign-in is launched.
3. Ben performs local deterministic interpretation and supplies bounded supporting context.
4. Gemini 3.8 Flash is called through Firebase AI Logic with Google Search grounding.
5. Gemini synthesizes current web evidence.
6. Grounding sources are displayed with the final result; Google's required Search entry point is displayed when supplied.

## External setup required
The source intentionally does not contain a real Firebase project configuration. To activate the feature, place the user's Firebase-generated `google-services.json` at `app/google-services.json`, enable Google sign-in, register the correct signing fingerprints, configure Firebase AI Logic, and enforce Firebase App Check/authenticated-user mode.
