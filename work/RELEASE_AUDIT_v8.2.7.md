# Rovex v8.2.7 — Release Audit

## Fix after CI compile failure
CI reported `MainActivity.kt:55:13 'onFling' overrides nothing`.
The `GestureDetector.SimpleOnGestureListener` implementation was removed and replaced with a direct `ScrollView` touch listener that tracks ACTION_DOWN/ACTION_UP and detects a left swipe. This avoids Kotlin/Android listener-signature compatibility issues while preserving normal vertical scrolling (`return false`).

## Requested features retained
- Two configurable colour-flow hues with mixed midpoint preview.
- Option to render colour-flow labels as normal text.
- Configurable greeting-panel colour.
- Left swipe on home dashboard opens Flashcards.
- Ren card has animated colour-flow border.
- Ren lettering performs sequential jumping animation.

## Stability audit
- XML resource parsing: PASS.
- No remaining `override fun onFling` in app source.
- No unsafe `execSQL(...PRAGMA...)` pattern found.
- Version: 8.2.7, versionCode 95.
- applicationId: `com.localqbank.library` (same as prior release for update installation/data retention).

## Build verification
Local Gradle compilation could not be executed because Gradle 8.11.1 is not cached and this environment cannot resolve `services.gradle.org`. GitHub CI must perform the authoritative compile test.


## v8.2.9 follow-up — Ren visibility
- `RovexJumpTextView` now resolves normal text colour through `ThemeManager.text(context)` when colour flow is disabled, preventing dark text on dark/AMOLED backgrounds.
- When colour flow is enabled, Ren uses the same configurable two-colour gradient as the animated border, while preserving the sequential letter-jump animation.
- Package ID remains unchanged for update-over-install.
