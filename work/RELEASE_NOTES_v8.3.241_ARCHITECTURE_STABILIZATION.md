# Rovex v8.3.241 — Architecture Stabilization

- Added Firebase Crashlytics production crash/ANR reporting dependency and privacy-bounded diagnostic wrapper.
- Crash reporting initializes only in the main process; the isolated Ben inference process remains lightweight.
- Added shared accessibility policy for newly touched dashboard controls: semantic descriptions, keyboard/focusability, and 48dp minimum touch targets.
- Extracted the newly redesigned dashboard labels/accessibility text into Android string resources.
- Preserved the existing manual AppContainer/manager architecture; no risky DI/Room/Compose rewrite.
- Kept QuizActivity/ViewModel/controller boundaries intact for a later controlled decomposition.
- Version 8.3.241 / versionCode 335.

Build status: static validation only; Android Gradle compilation remains the CI gate.
