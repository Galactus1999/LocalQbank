# Rovex v8.3.11 Release Audit

## Changes
- Launcher adaptive icon is R-only; the previous Rovex wordmark/artwork is removed from the launcher foreground.
- Legacy launcher icon is also R-only and centrally aligned.
- Main header uses a custom cube-settling R mark with bounded one-shot animation.
- Header R derives its colors from `ThemeManager`, so it follows the active Rovex theme.
- Header wordmark/subtitle removed to keep the R mark visually central and uncluttered.
- Provenance anchor refreshed for v8.3.11/versionCode 109.

## Safety audit
- Preserved package/applicationId `com.localqbank.library`.
- Preserved compileSdk 37, targetSdk 36, Java 17 and zstd Android AAR.
- No database/business-logic ownership changes.
- Logo animation cancels on detach and uses no threads, handlers, wake locks, I/O, or network.
- `findViewById<RovexWaveLogoView>` remains matched to the custom View declared in `activity_main.xml`.
- Duplicate layout IDs are checked per layout file only.

Provenance SHA-256: `4bf3e01e9d1952f61c94a877cc248957ee24ba3f53080925d7b430ce6d89679b`
