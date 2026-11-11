# Rovex Dark Liquid Glass UI Direction

This document treats the supplied PyQt6 dashboard as a visual reference only. Do not port Qt classes into Android, replace real Rovex data with demo metrics, or copy the fixed 430x920 layout.

## Three dark-theme treatments

- AMOLED: near-black translucent base, restrained cool-white edge glints, sparse theme accent on selected rim segments. Preserve true dark areas; do not lay a blue haze over the whole screen.
- Space: deep navy/indigo glass with controlled violet-blue highlights and crisp type. No persistent animated starfield behind study controls.
- Pandora: derive the surface and accent colors from the actual Pandora theme profile in ThemeManager. Use a dark jewel-toned surface and complementary rim; do not guess or hardcode the accent.

## Shared design language

- Compact header and clear app identity.
- Search field with a subtle top highlight and readable hint.
- Motivation card with a small accent label and quiet quote typography.
- Progress card with a dominant data-driven number and a thin custom progress indicator; avoid Unicode box-drawing glyphs.
- Theme-aware pill-shaped primary action with clear pressed, disabled and focus states.
- Compact two-column feature grid with consistent icon, title, description and metadata alignment.
- Floating bottom navigation only if it preserves existing navigation semantics, system insets, scroll behavior and accessible touch targets.

## Android implementation constraints

- Preserve existing Rovex screens, real data, navigation and Glass/Normal preference.
- Use the existing shared surface-style primitive as the styling authority; do not create parallel per-screen styling systems.
- Theme colors must come from ThemeManager and the existing theme profiles. Validate all three palettes against actual source before changing them.
- Static layered gradients, subtle highlights and thin rim strokes are the default. No unconditional live backdrop blur, full-screen blur, or always-on animation.
- Do not assume QGraphicsBlurEffect or Android RenderEffect automatically blurs arbitrary sibling/background content.
- True backdrop blur/refraction must be an isolated prototype with GPU overdraw, memory, frame pacing and thermal measurements before any global rollout.
- Respect responsive layouts, text scaling, accessibility contrast, system insets, and actual device aspect ratios. No absolute-positioned nav or fixed desktop-sized window.
- Capture actual Android screenshots for AMOLED, Space and Pandora; review against the reference and run whole-project, startup-risk and full CI audits before claiming completion.

## Acceptance criteria

1. Glass/Normal is visible, persistent, independent of theme and applies consistently.
2. AMOLED remains genuinely dark; Space reads as cosmic indigo; Pandora matches its actual theme palette.
3. Text and study controls remain legible; no white wash, clipped cards, overlapping navigation or decorative lines through content.
4. Existing study data, Home interactions and navigation are unchanged.
5. Full CI passes and real-device screenshot/thermal review is recorded. A source-only or CI-only pass is not visual acceptance.
