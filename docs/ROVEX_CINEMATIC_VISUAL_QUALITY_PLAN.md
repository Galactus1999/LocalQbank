# Rovex Cinematic Visual Quality & Performance Plan

Status: execution plan; no scene is considered shipped until its Android output and sustained device behavior pass the acceptance gates below.

## Objective

Build a small collection of exceptional, optional, theme-aware visual scenes for Rovex: black holes, galaxies/nebulae, forests/trees, rivers/waterfalls, and particle creatures. Visual quality—not effect count—is the primary product metric. The study workflow, readability, battery life, thermal stability, startup reliability, and reduced-motion preferences take precedence over decorative motion.

## Non-negotiable constraints

- Keep full-screen ambient wallpaper motion off by default. Do not reintroduce always-on animated backgrounds.
- Keep glass rims/corner glints static, restrained, theme-adaptive, and independent of scene motion.
- Scene motion must be optional, lifecycle-aware, and stopped when not visible. Respect Android animator/reduced-motion policy.
- Use the existing authoritative app managers and performance/thermal policy. Do not add a parallel governor or scheduling owner.
- Do not block foreground study or run expensive scene generation on the UI thread.
- Every overlay/change must be reproducible from the exact source archive selected by CI and fail closed on unexpected source anchors.
- Do not claim device validation based on JVM/Gradle CI alone. Do not merge the visual PR until the full CI and device acceptance are complete.

## Production method: art direction + hybrid rendering

Use the least expensive technique that meets the visual target, rather than forcing every effect into one renderer.

| Scene | First implementation candidate | Quality-critical elements |
|---|---|---|
| Black hole | Full-screen or localized GPU shader; layered authored starfield/disk textures; controlled gravitational-lensing approximation | Non-uniform accretion disk, warped background stars, layered plasma filaments, disciplined highlight rolloff |
| Galaxy / nebula | Multi-scale procedural shader layers plus authored cloud/dust textures and star points | Turbulent structure at several scales, dust lanes, depth separation, subtle parallax, restrained bloom |
| Forest / trees | Authored layered scene first; evaluate 3D only if the reference requires it | Strong silhouettes, detailed canopy layers, atmospheric perspective, restrained fog and localized light |
| River / waterfall | GPU flow shader with normal/flow maps, reflection and foam layers; authored environment | Coherent flow direction, ripples at multiple scales, shoreline interaction, foam/reflection behavior |
| Particle creature | Authored silhouette/mesh or hand-authored point cloud; precomputed paths, fine filaments and controlled trails | Recognizable anatomy, uneven density, coherent movement, graceful emergence/dissolution |
| Glass surfaces | Native static drawing or lightweight shader | Precise edges, thin theme-colored highlights, no always-on blur/animation |

Filament may be evaluated as an option for scenes that need 3D physically based materials and lighting; it is not a default dependency. Custom GPU shaders or layered 2D scenes may be better for individual effects. Do not add a large rendering dependency until a benchmark prototype demonstrates that it materially improves quality.

## Phases

### Phase A — Establish trustworthy baseline
1. Preserve the current Phase 2 branch and keep the PR unmerged.
2. Confirm all required CI workflows pass on the exact current head.
3. Obtain a reproducible build artifact and source snapshot from that same green run.
4. Capture baseline startup, Home, theme switching, Glass/Normal toggle, memory, frame pacing, and thermal behavior on the target device when device access is available.
5. Keep static glass and removal of always-on wallpaper motion separate from future optional scenes.

### Phase B — Visual specification and prototype harness
1. Create reference boards and scene-specific visual specifications before coding.
2. Define output size/aspect, focal point, palette, lighting hierarchy, depth layers, motion duration/speed, reduced-motion/static fallback, and forbidden artifacts.
3. Create a preview harness with fixed seed/time, parameter controls, and repeatable screenshots so changes can be compared fairly.
4. Create a small asset manifest recording provenance/license, source/hash, dimensions, color space, and target theme.
5. Establish before/after comparison at device resolution. Editor previews alone are not acceptance evidence.

### Phase C — Two flagship prototypes
Build the black-hole scene and particle-creature scene first because they test distinct technical approaches.
- Black hole: starfield warping/lensing, structured accretion disk, layered emission, carefully controlled bloom and tone mapping. Keep the effect physically inspired, not falsely advertised as a scientific simulation.
- Particle creature: anatomically recognizable point distribution, high-frequency fine particles/filaments, coherent motion and fading trails. Avoid uniform random dots or generic ribbons.
- Build a static master before adding motion. A weak still image cannot be rescued by animation.
- Prototype outside the foreground study flow first; integrate into splash/visual gallery only after review.

### Phase D — Scene-specific production
Extend only after both prototypes meet visual and performance gates. Produce galaxies/nebulae, forest/trees, and rivers/waterfalls using the appropriate rendering approach per scene. Do not reuse a single particle effect with only a palette change.

### Phase E — Android integration and adaptive quality
- Use bounded textures, precomputed geometry/point clouds, reused buffers and objects, and no per-frame allocation where practical.
- Bound resolution, frame rate, particle count, shader passes, cache sizes and asset decode work.
- Scale quality down before thermal stress; do not wait for overheating.
- Stop rendering when hidden/backgrounded; cancel work safely and release resources.
- Retain an attractive static fallback and make motion opt-in.
- Keep custom wallpapers and the current theme/Glass/Normal preferences working.

### Phase F — Validation and release
For each scene:
1. Run Python/static source audits, XML checks, per-layout duplicate-ID checks, source/version/overlay assertions, and whole-project scan.
2. Cross-check every findViewById<T>() against actual XML widget type and inspect startup/onCreate code for crash risks.
3. Run the full CI pipeline: JVM tests, debug build, Android test target, instrumented tests where available, release build, signing fingerprint verification, artifact/source packaging, and final error/incompleteness rescan.
4. Review screenshots across Light, Pastel, Mint, Sunset, Lavender and AMOLED; verify contrast, clipping, focal composition and theme mapping.
5. On the target device, profile cold/warm start, steady-state frame pacing, CPU/GPU, memory, battery and thermal trend over an extended session, plus navigation/background/foreground and reduced-motion cases.
6. Record evidence and limitations. A green CI build does not prove visual quality or real-device thermal acceptance.

## Initial quality scorecard

Rate each category from 1–5 against the reference, recording examples rather than relying only on an aggregate score:
- Composition and focal hierarchy
- Shape/silhouette accuracy
- Fine detail and multiscale texture
- Lighting, contrast, color separation and highlight rolloff
- Depth/atmosphere/parallax
- Motion coherence and temporal stability
- Theme adaptation and text/readability compatibility
- Artifact-free edges, blending and scaling
- Performance/thermal stability and lifecycle correctness

A scene is not approved if it scores poorly on any critical visual dimension or fails any safety/performance gate, even if its average score is high.

## Immediate next actions

1. Preserve and verify the current Phase 2 static-glass/no-background-motion baseline.
2. Build the prototype harness/specification before adding a rendering library.
3. Produce the black-hole static master and shader prototype; review at target-device resolution.
4. Produce the particle-creature prototype using the supplied video as motion/style reference.
5. Compare techniques and measured cost; only then choose the implementation approach for each scene.
6. Expand to nebulae, forest, and water only after the prototype pipeline is proven.

## Current evidence boundary

CI can establish compilation, automated tests and reproducible packaging. It cannot establish that a scene looks premium, is smooth on the target phone, or avoids long-session heating. Those claims require screenshots, profiling and physical-device validation.
