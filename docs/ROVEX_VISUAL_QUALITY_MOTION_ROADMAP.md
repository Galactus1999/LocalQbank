# Rovex Visual Quality & Motion Roadmap

Status: active implementation plan
Target branch: `rovex-visual-engineering-foundation`
Owner: Rovex engineering workflow
Principle: stability, readability, and measured performance outrank novelty.

## Product direction

Build a distinctive premium medical-study interface—not a generic pastel dashboard, a collection of glowing boxes, or an animation showcase that distracts from solving questions. The interface should feel coherent across light, dark, AMOLED, and other supported themes. The Pastel theme remains supported for compatibility but is not the visual target.

## Design principles

1. **Study-first hierarchy.** Question stems, options, explanations, progress and controls remain immediately legible. Motion must never compete with question solving or delay input.
2. **Semantic theme tokens.** Components consume shared theme roles for surfaces, text, borders, accents, focus, and state. Decorative motion must not depend on text-flow colour preferences.
3. **Restrained depth.** Use glass/blur selectively for overlays, menus, and focused panels. Main study content should prefer stable surfaces; avoid stacking gradients, borders, shadows, and glows.
4. **Compact geometry.** Use consistent spacing, type hierarchy, and bounded card sizes. Fix oversized/empty cards at their layout source rather than hiding the issue with visual effects.
5. **Signature, not noise.** Use a few recognizable motion moments: a breathing accent, responsive logo/mascot, purposeful transitions, and tactile feedback. Avoid continuous motion everywhere.
6. **Accessibility and control.** Maintain contrast, non-colour cues, readable typography, reduced-motion accommodation where available, and stable states for users who disable animation.
7. **Offline-first and licensing.** Prefer locally bundled assets with verified provenance and licence metadata. Never introduce a runtime network dependency for decorative assets. Do not generate substitute animation when a suitable licensed asset can be imported.
8. **Performance budgets.** Pause off-screen animation, avoid work on the main thread, and measure startup, frame pacing, memory, APK size, and battery impact before wider rollout.
9. **Evidence-based completion.** Figma references are not proof of Android implementation. Compare actual rendered Android screenshots against the design and report design, source, build, test, and device verification separately.

## Recommended visual direction

- Neutral charcoal/AMOLED foundations and clean light surfaces.
- Theme-adaptive electric blue and teal accents, selected per theme.
- Crisp typographic hierarchy and carefully spaced numerals.
- Compact progress indicators and glanceable study statistics rather than tall empty panels.
- Selective glass treatment on overlays and Ben+AI surfaces.
- Small, low-amplitude ambient light/breathing motion behind selected home cards.
- Purposeful spring/morph transitions for meaningful actions only.
- A recognizable Ben+AI visual identity with truthful states: idle, thinking, answering, unavailable. Never show fake loading activity.
- Keep question-solving content visually stable; animation must not reduce contrast or obscure content.

## Animation technology policy

| Technology | Intended use | Gate |
|---|---|---|
| Rive | Interactive logo/assistant, state machines, selected touch-driven animation | Verify Android runtime version, native ABI packaging, licence/provenance, accessibility, offline bundling, and measured performance before broad use |
| Lottie | Imported decorative loops/background effects | Verify source, licence, asset hash, JSON/runtime compatibility, bounded recolour cache |
| Native Android animation | Button state, navigation, progress, compact geometry | Prefer for simple transitions to avoid unnecessary runtime/dependency cost |
| Theme tokens | Palette, contrast, state and surface consistency | Must be the single authoritative source for theme-dependent visuals |
| Figma | Editable tokens, components, light/dark variants, motion contracts | Use as design specification; reconcile against actual app screenshots |

Do not add Rive merely because it is trending. Start with a small isolated proof of concept and add the dependency only if it provides a measurable advantage over existing Lottie/native solutions. Check for `libc++_shared.so` or other native packaging conflicts.

## Execution phases

### Phase 0 — Stabilise and verify (current gate)
- Fix current GitHub Actions failure from the failing test/log, not by weakening the workflow.
- Preserve the QBank deletion/recovery contract and verify the test reflects WorkManager's asynchronous execution semantics.
- Confirm current home-card theme-token correction, text-flow independence, cache bounds, and asset provenance.
- Run JVM tests, debug and Android test builds, instrumentation, final source/error/incompleteness rescan, release build, packaging, and signing checks.
- Do not call the build green until the complete required workflow succeeds.

### Phase 1 — Theme foundation and geometry
- Reconcile semantic colour/surface/spacing/radius/type/motion tokens between Figma and Android.
- Audit every supported theme, including light/dark/AMOLED and compatibility themes.
- Measure Home card bounds and eliminate oversized/empty sections at their source.
- Capture real rendered Android screenshots at a consistent viewport and compare with Figma.

### Phase 2 — Home motion polish
- Keep card motion independent from text-flow settings.
- Use imported, licensed assets only; start with restrained theme-adaptive breathing accents.
- Ensure animations pause off-screen and remain readable in all theme states.
- Add instrumentation/regression coverage for palette independence, theme changes, card count, opacity, and lifecycle/pause behavior.

### Phase 3 — Rive feasibility and pilot
- Inspect current official Rive Android documentation/runtime and relevant asset licences.
- Prototype one isolated interactive asset (preferably Ben/brand identity) without changing core study flows.
- Validate APK/native library packaging, offline operation, reduced-motion behaviour, accessibility, frame pacing, memory, and battery.
- Adopt only if the pilot passes the same CI and visual evidence gates.

### Phase 4 — Ben+AI experience
- Define truthful, accessible visual states that reflect actual engine status.
- Refine the panel's surface, spacing, typography, and transitions while protecting the underlying question readability.
- Ensure no animation blocks inference, UI response, or deterministic fallback.

### Phase 5 — Interaction and progress
- Add restrained native/Rive transitions to selected meaningful state changes.
- Compact dashboard statistics and progress visuals.
- Keep touch targets, accessibility semantics, and clear state cues intact.

### Phase 6 — Final quality and regression audit
- Compare rendered Android screenshots against the Figma reference for every relevant theme.
- Run whole-project source scan, XML parsing/duplicate-ID audit per individual layout, `findViewById<T>()` vs XML type checks, Activity startup/onCreate runtime-risk audit, dependency/API compatibility checks, and incomplete-code scan.
- Run JVM, Android instrumentation, debug and release build gates; verify APK identity/signing and package the source snapshot.
- Report exact commit, workflow URL, test counts, artifacts/screenshots, and unresolved limitations. Never infer visual success from a source diff.

## Acceptance criteria

A phase is complete only when:
- Its implementation is present in the source overlay/build input, not only in a generated local snapshot.
- Relevant regression tests pass.
- Required CI build/test and final audit gates pass.
- Actual Android rendering is inspected when the change is visual.
- Asset provenance/licensing is recorded.
- No unrelated QBank data, deletion, SRS, flashcard, import, theme, Ben, or stability contracts regress.
