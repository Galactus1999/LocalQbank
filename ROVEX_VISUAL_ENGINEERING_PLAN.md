# Rovex Visual Engineering Plan

Status: ACTIVE
Baseline: main @ 2026-10-08
Current product baseline: v8.3.615 / versionCode 701 source archive, with the 8.3.616 background repair applied by CI overlay when that exact archive is selected.

## Objective

Replace the current ad-hoc visual implementation process with a repeatable design -> asset -> component -> interaction -> performance -> device-validation pipeline.

The goal is not to add more decoration. The goal is to make every future visual/interaction idea land at production quality without sacrificing stability, accessibility, startup, battery, or QBank functionality.

## Non-negotiable rules

1. One visual subsystem change at a time.
2. Never replace a working renderer blindly.
3. Base backgrounds must remain stable/opaque; decorative motion is a separate bounded layer.
4. No continuous full-screen animation unless a measured requirement proves it is necessary.
5. No per-frame object allocation in custom renderers.
6. Assets must have verified redistribution licenses.
7. Animation must pause/stop with lifecycle/visibility.
8. Haptics and sounds are semantic events, not scattered Activity calls.
9. Visual values must migrate toward centralized design tokens.
10. Every major visual feature gets a performance budget and a regression check.
11. CI-green means the complete applicable workflow is green; an individual build job is never described as repository-green.
12. Preserve authoritative managers and existing deterministic/offline-first behavior.
13. Never mix risky visual refactoring with unrelated QBank/DB/AI repairs.
14. Every patch must be source-auditable and reversible.

## Phase 0 — Visual inventory and guardrails [IMPLEMENTED FIRST]

Deliverables:
- Automated visual architecture inventory.
- CI artifact containing the inventory report.
- Baseline measurements/counts for hard-coded colors, Canvas/shader use, animation frameworks, haptics, audio, theme calls, and patch/overlay mechanisms.
- No runtime behavior change.

Exit criteria:
- Audit runs against the exact unpacked candidate source used by Adaptive Android CI.
- Report is uploaded on every adaptive build.
- Audit itself is deterministic and has no source mutations.

## Phase 1 — Design system foundation

Create one authoritative visual token layer:
- Color roles: background, surface, elevated, text, muted, accent, semantic success/warning/error/info.
- Typography roles.
- Spacing scale.
- Shape/radius scale.
- Border/elevation policy.
- Motion durations/easings.
- Theme modes.

Migrate only one component family at a time. Do not mass-rewrite the application.

Exit criteria:
- New UI components do not introduce raw visual constants without an explicit exception.
- Light/dark/AMOLED contrast invariants are tested.

## Phase 2 — Visual Lab

Create a developer-only Visual Lab screen that renders:
- theme samples
- cards
- buttons
- progress
- QBank option states
- flashcard states
- selected/correct/wrong states
- dialogs
- motion states
- haptic/sound test controls

Purpose: design and performance iteration without navigating the entire application.

Exit criteria:
- All new visual components can be inspected in isolation.
- Screenshots can be captured as stable regression references.

## Phase 3 — Professional asset pipeline

Evaluate and adopt the correct tool per asset type:
- Figma for design system/components/tokens and high-fidelity layout.
- Figma Dev Mode/MCP when available for structured design handoff.
- Lottie for bounded vector playback where appropriate.
- Rive only where interactive/state-machine animation provides a real advantage.
- Graphite for open-source vector/raster/procedural asset creation, not as an APK runtime dependency.
- Native Android resources for simple/static artwork.

Every external asset must record:
- source
- author
- license
- source revision
- modifications
- redistribution permission
- attribution requirement

Exit criteria:
- No untracked third-party artwork.
- Assets are optimized and have explicit runtime size/performance limits.

## Phase 4 — Motion system

Create a semantic Rovex motion layer:
- press
- selection
- success
- error
- navigation
- expansion
- milestone/celebration
- ambient

Rules:
- bounded rendering
- lifecycle aware
- reduced-motion aware
- no duplicate animations
- no perpetual work when invisible
- no animation for information that must be conveyed through text/state alone

## Phase 5 — Interaction feedback system

Create semantic event ownership:
- ANSWER_CORRECT
- ANSWER_WRONG
- BOOKMARK
- FLASHCARD_MARK
- SERIES_COMPLETE
- STREAK_MILESTONE
- IMPORT_COMPLETE
- ERROR

One event may coordinate visual + haptic + sound, with global user controls and duplicate suppression.

This directly prevents regressions such as simultaneous duplicate answer sounds.

## Phase 6 — Rendering/performance engineering

Use:
- Android Macrobenchmark
- FrameTimingMetric
- Baseline Profiles
- Perfetto/System Trace
- Android Studio Profiler
- Layout Inspector

Critical journeys:
- cold launch
- Home -> QBank
- QBank -> subject -> test
- next/previous question
- Home -> Cards
- Home -> Mastery
- Frankenstein open/search

Record before/after:
- frame timing/jank
- TTID/TTFD
- CPU
- allocations/GC
- memory
- DB latency
- WebView cost
- animation cost

## Phase 7 — Visual regression

Introduce reference screenshots for a small, deliberate set of critical screens:
- Home light
- Home dark/AMOLED
- QBank light
- QBank dark/AMOLED
- Flashcards
- Study Health
- representative question states

Use thresholds rather than blind pixel equality. Fail only on meaningful structural/visual regressions.

## Phase 8 — Device validation

Primary physical validation device:
- OnePlus CPH2691 / Android 16

Emulator CI remains functional coverage; physical-device profiling is the performance truth.

## Phase 9 — Release gate

A visual change is complete only when:
- exact changed source inspected
- static/source audit passes
- XML/type/lifecycle checks pass where applicable
- JVM tests pass
- Android build passes
- instrumented tests pass where available
- visual regression passes where available
- performance budget passes for affected journey
- physical-device validation is completed for performance-sensitive changes
- signing/native payload invariants remain intact
- complete applicable CI workflow is green

## Current immediate sequence

1. Visual inventory/guardrails.
2. Review the inventory and classify KEEP / REPLACE / CONSOLIDATE / DELETE / MOVE.
3. Establish design tokens.
4. Build Visual Lab.
5. Migrate one component family.
6. Add professional assets/motion.
7. Add semantic feedback system.
8. Add visual regression/performance gates.
9. Only then perform larger visual redesigns.

This sequence deliberately prevents another cycle of large visual rewrites followed by screenshot-driven patching.
