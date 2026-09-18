# Rovex v8.3.243 — Stability/UI Polish

## Build-failure corrections
- Replaced the CI-failing `TODAY'S LOG` resource text with `TODAY LOG` after AAPT reported an invalid unicode-escape flattening error in `strings.xml`.
- Corrected `RenActivity` provider dialog button access to use the concrete `AlertDialog`, removing three `getButton` Kotlin compile errors.

## UI polish
- Main dashboard root now starts transparent and receives the active ThemeManager atmosphere at runtime.
- Performance Lab home entry is slightly taller for cleaner analytical summary presentation.
- About Rovex is now a compact structured information panel covering version, application ID, deterministic core, Ben architecture, data boundary, safety and UI system.

## Verification policy
- Static/source validation must pass before CI.
- Android release compilation is not claimed green until GitHub Actions completes successfully.
- After this compile correction, the next development phase is stability-only: CI, startup/runtime audits, Phase-2 Ben IPC/device stress, and regression correction; no feature expansion unless required to fix a stability defect.
