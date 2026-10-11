# Rovex Liquid Glass: Renderer Research and Integration Decision

Status: research-backed implementation direction; not yet accepted as shipped visual behavior. The current Phase 670 renderer is a static highlight overlay and is not true backdrop refraction.

## Findings

### Candidate renderer

The strongest fit found for Rovex's existing Android View/programmatic-View UI is [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android), MIT-licensed, with a `LiquidGlassView` `FrameLayout`, a classic fallback pipeline for API 24+, and an AGSL lens pipeline on API 33+. Its documented features include backdrop capture/blur, rounded-rectangle SDF refraction, chromatic dispersion, edge/specular highlights, and accessibility fallbacks. This is a much closer match to the requested physical material than manually drawing arcs over a `LayerDrawable`.

The current device target (OnePlus SM8650 / Android 16) is capable of the API 33+ AGSL path in principle. That does not establish compatibility, speed, or visual quality in Rovex: it must be tested in the actual project and on-device.

### Important technical restrictions and failure modes

1. **A shader overlay is not a glass renderer.** A radial gradient plus rim cannot bend or blur the pixels behind a card. Keep Phase 670 described as a temporary fallback only.
2. **Backdrop sampling requires the right view order and source.** The candidate's README says background content must be drawn before the glass in an overlapping parent. Dynamic backdrops must enable `enableDynamicBackground`; otherwise capture may be stale. Dialogs/bottom sheets may need an explicit `backdropSource` because their own window parent can be transparent.
3. **API tiers differ.** The repository documents AGSL lens/refraction on API 33+ and a classic native blur/aberration path on API 24–32. Do not assume every property has the same effect on all API levels; query/report the effective pipeline and retain a readable opaque fallback.
4. **Native and dependency supply-chain risk.** The candidate publishes a JitPack AAR with prebuilt native libraries for arm64-v8a and armeabi-v7a. CI must resolve the exact pinned version, verify its license and packaged ABIs, and fail closed if artifacts cannot be fetched. Do not add NDK toolchain work or an unpinned dynamic dependency casually.
5. **Cost scales with live backdrop area and invalidation frequency.** Avoid eight independent full-screen captures, per-card full-resolution blur, continuous invalidation, or sensor-driven animation on every card. Start with a small, bounded number of glass groups, static highlight, modest blur/downsampling, no touch deformation, and no sensor highlight. Profile GPU/frame time, allocations, memory, battery and thermal state before expanding.
6. **Readability/accessibility wins.** A wallpaper can have very high local contrast and ruin text even when the average tint is attractive. Use a readability-first material for text-heavy cards, use a clear material only for short controls over suitable detail, and honor high contrast, reduced motion, battery saver and the user's Normal/Solid preference.
7. **The library is a candidate, not an unquestioned dependency.** Verify its current source, issue history, build reproducibility, transitive dependencies, lifecycle cleanup and licenses. The upstream demo's own performance claims are not evidence of Rovex performance.

## Implementation decision

Use a **renderer adapter behind the existing `RovexVisualSurfaceStyle` authority**, not a second global styling system. Preserve the persisted Glass/Normal preference and the real data/navigation inside the existing views.

### Staged acceptance gates

- **Gate A — dependency and source audit:** pin an immutable release/tag; inspect the actual AAR/module and MIT license; verify JitPack resolution and all shipped ABIs; check minimum SDK, compile SDK, AGP/Kotlin compatibility and transitive dependencies. Do not change the app-wide source of truth yet.
- **Gate B — isolated showcase:** add a debug-only preview screen/flag using the actual Rovex wallpaper and real theme palette. Demonstrate the same source content behind glass and compare static fallback vs. library renderer. Test AMOLED, Space, Pandora, Light, Pastel, Mint, Sunset and Lavender.
- **Gate C — lifecycle and rendering tests:** verify capture order, scrolling, moving content, theme/wallpaper changes, activity pause/resume, recycled/recreated views, bottom navigation, window insets, large text and contrast. Confirm no stale backdrop or black/transparent capture.
- **Gate D — measured device profiling:** compare baseline and glass on the target device with Perfetto/FrameTimeline or equivalent. Record missed frames/jank, GPU time, memory, allocation rate, power/thermal drift, and time-to-stable frame. Test scrolling and rapid navigation for several minutes, not just a static screenshot.
- **Gate E — limited production integration:** only after A–D pass, replace a small number of existing surfaces via the shared adapter. Roll out progressively; keep Normal/Solid and static fallback available. Do not wrap every tile in its own independent renderer.
- **Gate F — full regression:** source/XML/ID audit, `findViewById<T>()` vs actual XML type checks, startup crash review, JVM/debug/android-test/release/signing CI, and screenshot comparison across all eight themes. No merge on CI-only evidence; visual and thermal acceptance are separate gates.

## Design parameters to start testing (not declared final)

- Prefer readability-first/regular glass for Home progress and text-heavy cards.
- Reserve clear glass for search/pill navigation only when the wallpaper behind it contains meaningful detail.
- Start with low-to-moderate blur, restrained tint opacity, narrow edge highlight, dispersion disabled or very low, no always-on sensor tracking, no press bulge, no perpetual animation.
- AMOLED must retain near-black interiors; Space uses its real theme indigo palette; Pandora uses its actual ThemeManager palette. Other five themes need distinct wallpaper and appropriately adapted glass/tint, not one universal blue overlay.
- The wallpaper remains full-opacity behind glass. Never darken or blur the entire screen to make cards look more glass-like.

## Research references

- QWEA0, Liquid Glass Android (API tiers, hierarchy/backdrop requirements, tuning, MIT license): https://github.com/QWEA0/Liquid-Glass-Android
- Android Open Source Project, Window blurs (distinguishes same-window RenderEffect from cross-window blur and warns that cross-window blur is expensive; blur radius and translucent tint must be balanced): https://source.android.com/docs/core/display/window-blurs
- Android Developers, Android 12 RenderEffect overview (View RenderEffect affects a RenderNode's contents; Window background blur is a separate API): https://android-developers.googleblog.com/2021/03/android-12-developer-preview-2.html
- Google Filament: https://github.com/google/filament — capable PBR engine, but not the default choice for 2D UI glass. A full 3D engine adds native lifecycle, swap-chain, threading and memory complexity without solving ordinary View backdrop sampling by itself.

## Current state

- Phase 670 is a low-cost static overlay approximation, not physical glass.
- A previous CI run failed during source discovery because the visual asset audit still expected six themes after the catalogue expanded to eight. The audit assertions are being aligned; this is a source-discovery failure, not an Android compile result.
- No claim of actual library integration, successful Android build, screenshot acceptance, or target-device performance is made until those gates produce evidence.
