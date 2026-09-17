# Rovex 8.3.225 — Frankenstein / Ben Model Router

## Changes
- Corrected the CI compiler defect in `RenActivity`: TextView background is now set through `setBackground(...)`.
- Added `BenModelRouter` as the single model-selection boundary.
- Local Ben remains the authoritative path.
- Hybrid mode combines local Ben/Frankenstein context with Gemini only when current/external evidence, explicit Gemini/web research, or complex synthesis makes it useful.
- Offline mode never attempts cloud inference.
- The router is policy-only and does not own QBank, SRS, notes, learner memory, or medical truth.
- Added a visible router status strip in Dr. Frankenstein UI.
- Combined core now visibly reports its route and falls back to the local result if Google/Gemini is not connected.

## Firebase/Gemini UX
The user-facing flow remains one Google account action. Firebase AI Logic is the secure cloud transport; no Gemini API key is embedded in the APK.

## Validation status
Static/source checks must pass in CI. Android compilation is not certified by this environment unless CI completes successfully.
