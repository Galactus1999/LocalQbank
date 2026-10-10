# Rovex Visual Architecture Baseline — v8.3.615

Audit date: 2026-10-08
Source: `Rovex_v8.3.615_Clinical_Day_Imported_Motion_Phase_Source.zip`
This baseline describes the source archive before the v8.3.616 CI background overlay. It is an inventory, not a defect count.

## Inventory

- 438 text source/config files
- 48,551 source lines
- 224 hard-coded hex color literals across 37 files
- 425 direct Android Color API references across 52 files
- 165 GradientDrawable references across 39 files
- 193 Canvas/draw calls across 23 files
- 15 RuntimeShader references across 10 files
- 43 other shader/gradient references across 12 files
- 40 Android animation API references across 11 files
- 31 Compose-style animation references across 15 files
- 10 Lottie references across 2 files
- 23 haptic references across 4 files
- 10 audio references across 2 files
- 66 direct background assignments across 29 files
- 41 alpha mutations across 14 files
- 1,243 ThemeManager/RovexThemeEngine references across 60 files

The audit intentionally counts occurrences, not unique implementations. High counts are signals for consolidation, not automatic bugs.

## Initial classification

### KEEP / strengthen

- `ThemeManager` as the authoritative theme-token owner.
- `RovexThemeProfile` as the profile data source.
- Existing Lottie dependency/use where bounded vector playback is actually appropriate.
- `RovexTouchFeedback` as the centralized touch/haptic entry point.
- `RovexSoundFeedback` as the centralized low-latency sound layer.
- `RovexMotion` / motion policy as the seed of a semantic motion system.
- Existing Macrobenchmark / baseline-profile direction.
- Existing CI source-discovery and version-specific overlay safeguards.

### REPLACE / REWORK later, not in this batch

- Full-screen animated shader backgrounds as the default visual foundation.
- Large custom Canvas renderers where an authored asset or standard Android component is more appropriate.
- Broad theme-walking heuristics that infer semantic component type from resource IDs/class names.
- Runtime visual constants that duplicate values already owned by ThemeManager/ThemeProfile.

### CONSOLIDATE

- Hard-coded colors and repeated Color.rgb/argb calls.
- GradientDrawable construction scattered through Activities/controllers.
- Animation ownership spread across views and controllers.
- Semantic event ownership for sound + haptic + visual feedback.
- Visual assets and their provenance/license metadata.

### DELETE only after proof

- Dead/duplicated visual renderers.
- Superseded shader implementations.
- Historical patch/overlay logic that no longer has a live baseline.
- Duplicate feedback paths.

Nothing in this section is a permission to delete code yet. Each candidate requires exact-source inspection and regression proof.

## Important discovery

The source already contains mature seeds for the system we want:
- Lottie is already present.
- Touch feedback is already centralized.
- Sound feedback is already centralized.
- Motion policy/classes already exist.
- ThemeManager is already intended to be the sole visual-token owner.
- Performance tooling already exists.

Therefore the next move is **consolidation and contract enforcement**, not adding a pile of new libraries.

## Next implementation batch

1. Establish explicit visual-token contracts around the existing ThemeManager/Profile.
2. Build the Visual Lab around existing components.
3. Add a semantic motion contract without changing existing motion behavior.
4. Add visual asset provenance metadata.
5. Measure the current critical journeys before changing renderer architecture.
6. Only then migrate one visual family at a time.

## Safety

This baseline does not authorize:
- QBank/database changes
- AI/Ben changes
- search changes
- importer changes
- renderer rewrites
- dependency upgrades
- removal of existing corrected features

Those remain separate workstreams.
