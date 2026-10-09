# Rovex Visual Quality & Performance Execution Plan

**Repository:** `Galactus1999/LocalQbank`  
**Working branch:** `rovex-visual-engineering-foundation`  
**Plan status:** Approved execution plan; phases are gated by evidence, not calendar dates.  
**Primary objective:** Improve visual quality, responsiveness, startup, memory, graphics, and sustained performance without removing existing functionality or weakening safety and correctness.

## Non-negotiable engineering rules

1. **Evidence before edits.** Reproduce the defect, identify its owning code path, capture a baseline, and state the expected invariant before modifying source.
2. **Small, auditable changes.** One root cause or tightly coupled change per patch/commit. Avoid unrelated edits and giant one-off scripts.
3. **No god files.** Do not expand already-large activities, database façades, or managers with unrelated responsibilities. Put new work in small, cohesive classes/modules with clear ownership and tests. Extract existing code only when measurements or responsibility boundaries justify it.
4. **Preserve authoritative owners.** `AppManagers` owns engines/managers. Keep `PerformanceManager`, `AdaptiveEngineManager`, `ResilienceManager`, `EngineHealthManager`, `EngineMeshCoordinator`, `IntelligenceOrchestrator`, `StudyCore`, and `ImportPipelineCoordinator` authoritative in their domains. `RovexBatteryManager` is a policy/governor, not a second scheduler or business-logic owner.
5. **Preserve features and data.** Do not regress QBank import/render/search, incremental FTS deletion cleanup, SRS, manual-only flashcards, notes, backup/restore, themes, touch feedback, Ben+AI, offline operation, or deterministic fallback.
6. **No performance-by-degradation.** Do not improve a benchmark by lowering original artwork quality, disabling existing features, truncating study content, or removing necessary tests.
7. **Incomplete-code scan is mandatory.** Search for TODO/FIXME/placeholder/stub/not-implemented markers, dead/unwired classes, unfinished branches, swallowed errors, and feature flags that permanently disable intended behavior. Classify each result; repair genuine incompleteness and document intentional placeholders with an owner/reason.
8. **Final audit is mandatory after every phase.** Scan changed and adjacent source for compile/type errors, XML/manifest defects, startup/runtime crash risks, lifecycle leaks, architecture violations, regressions, missing wiring, and incomplete work. Fix findings before calling a phase complete.
9. **CI honesty.** Never claim a build is green until the relevant workflow completes successfully. Inspect the exact failed step and logs before patching. A source audit or successful unit test is not a release build.
10. **No blind dependency or asset imports.** Check current official documentation, repository maintenance/activity, license, API stability, Android compatibility, transitive dependencies, security, and device performance before adoption.
11. **No unverified asset redistribution.** The user authorizes research and imports, but every asset still needs a recorded source, license/terms, attribution requirements, dimensions, hash, and permitted use. Do not bypass access controls or licensing restrictions. Prefer CC0/explicitly compatible assets and a curated catalogue.
12. **No unrelated feature redesign during a repair.** Keep root-cause fixes scoped and use rollback-safe commits.

## Research and evidence sources

Use current, first-party and high-quality sources before choosing APIs or dependencies:

- Android performance overview: https://developer.android.com/topic/performance/overview
- Measuring app performance: https://developer.android.com/topic/performance/measuring-performance
- Android app optimization guidance: https://developer.android.com/topic/performance/appstartup/best-practices
- Baseline Profile measurement: https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile
- Android performance samples (Macrobenchmark, Microbenchmark, JankStats): https://github.com/android/performance-samples
- Perfetto tracing and latency diagnosis: https://github.com/google/perfetto
- Android bitmap optimization: https://developer.android.com/develop/ui/compose/graphics/images/optimization
- Coil image loader documentation (evaluate against current dependencies before adoption): https://coil-kt.github.io/coil/image_loaders/
- Kotlin, AndroidX, Android Developers release notes and official API reference.
- GitHub code search/repository issues and source history; inspect implementation and maintenance before reusing code.
- Asset sources and licenses: https://polyhaven.com/license, https://commons.wikimedia.org/wiki/Commons:Reuse, https://images.nasa.gov, https://unsplash.com/license, https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines, https://www.pexels.com/license/, https://creativecommons.org/share-your-work/cclicenses/

Research claims must be dated/linked in implementation notes when version-sensitive. Third-party examples are evidence to inspect, not code to import blindly.

## Phase 0 — Recover a trustworthy baseline and keep CI green

**Goal:** Establish exactly what source is built and make the current CI candidate pass before new visual/performance implementation.

- Inspect latest branch head, PR checks, selected source archive, overlay order, generated source, and exact failed step/log.
- Repair the known failing unit test in the exam-mode prompt contract. The test must enforce four distinct purposes and the mandatory approved-source policy without requiring identical wording for legitimate policy synonyms.
- Ensure source overlays are deterministic, idempotent where expected, and fail closed when anchors differ unexpectedly.
- Keep final source/error/incompleteness scan and ensure diagnostics are uploaded even on failure.
- Record source archive SHA, commit SHA, build/test/release outcomes, and remaining warnings.
- Do not start product changes until CI baseline is verified green.

**Exit gate:** JVM tests, debug build, Android test build/tests when available, release build and all required workflow checks succeed on the actual candidate source.

## Phase 1 — Measure current performance and visual truth

**Goal:** Build a reproducible baseline before tuning.

Measure cold/warm/hot startup and time to initial/full display; Home refresh; Home → QBank → subject → test; next/previous question; search; bookmarks; cards; notes; imports/deletion; chat; Ben+AI; theme switching; background image load; and long-session behaviour.

Collect Macrobenchmark results, P50/P90/P95/P99 where meaningful, frame timing/jank, main-thread stalls, CPU scheduling, database/FTS timings, image decode/crop/cache activity, heap/native memory, GC, WebView memory, inference latency, thermal state, and battery behaviour. Use Perfetto traces for suspected bottlenecks and physical-device measurements for representative graphics/thermal results.

Capture comparable screenshots for every theme and target screen. Record source asset dimensions, decoded bitmap dimensions, crop, alpha, overlays, and resource mapping. Distinguish confirmed root causes from hypotheses.

**Exit gate:** Baseline report and reproducible journeys exist; no optimization is accepted without a before/after comparison.

## Phase 2 — Repair wallpaper fidelity and theme identity

**Goal:** Fix the existing quality regression before adding more motion.

- Trace source asset → asset pipeline → decode/resize → crop → theme mapping → drawable/view → opacity/scrim → final compositing.
- Determine whether blur/softness comes from low-resolution assets, wrong derivatives, excessive sampling, repeated scaling, opacity, overlay stacking, or a theme mapping defect.
- Preserve source images; never upscale low-resolution images and call them 4K.
- Create a curated theme-to-asset manifest with source URL, creator, license, attribution, retrieval date, SHA-256, original dimensions, crop/focal point, target dimensions, and asset type.
- Build distinct, coherent identities (for example Obsidian/AMOLED, Pastel Prism, Emerald/Nature, Titanium/Graphite, Ocean/Aurora) rather than changing only accent colour.
- Use only assets whose license and distribution/API terms permit the exact intended use. Prefer CC0/compatible assets; do not bulk redistribute assets under terms that prohibit wallpaper products or standalone redistribution.
- Keep first-run APK asset size bounded; optional packs may be imported/downloaded on demand with explicit cache/storage management.
- Keep foreground text/options readable without globally washing out the artwork. Preserve pure-black AMOLED surfaces where the theme requires them.
- Add automated manifest/license-metadata validation and screenshot comparisons.

**Exit gate:** Correctly mapped, genuinely high-resolution backgrounds render sharply and distinctly in all themes; no contrast, readability, APK-size, or startup regression.

## Phase 3 — Efficient static image pipeline

**Goal:** Keep high source quality without excessive memory or frame cost.

- Inventory current image-loading and cache dependencies before adding any new dependency.
- Decode off the main thread and size decoded images to actual display bounds/density; retain originals separately from display derivatives.
- Use bounded memory and disk caches with deterministic keys and explicit invalidation on theme/asset changes.
- Avoid repeated transforms, per-screen duplicate decode, unbounded bitmap retention, and loading full-resolution images into thumbnails.
- Use WebP/AVIF only where Android compatibility, visual quality, decoder support, and asset pipeline make sense; compare file size and visual quality.
- Provide a stable theme-matched fallback during load/failure; avoid layout shifts and blank frames.
- Test low memory, process recreation, rotation/density changes, cache corruption, offline mode, and rapid theme switching.

**Exit gate:** Lower measured decode/peak-memory cost and no visible quality degradation, stale theme images, crashes, or blocking main-thread I/O.

## Phase 4 — Lightweight live wallpaper and motion system

**Goal:** Make backgrounds feel alive without competing with study.

- Audit the existing animation/Lottie/colour-flow implementation and reuse a single authoritative motion path where practical.
- Prefer GPU-friendly gradient/colour interpolation and a small number of animated highlights; use video/full-screen 4K animation only as an optional mode after profiling.
- Do not allocate objects, parse JSON, decode assets, perform I/O, or do expensive theme resolution per frame.
- Pause animation when hidden/stopped; resume safely without duplicate callbacks or runaway frame loops.
- Respect reduced-motion/user settings, thermal/power policy, visibility, and foreground study priority.
- Keep animation parameters bounded and expose status/control in the existing settings surface rather than inventing a parallel settings owner.
- Validate touch/navigation/scrolling while motion is active and inactive.

**Exit gate:** Motion is visibly present where intended, lifecycle-safe, smooth on the target device, and does not materially worsen frame-time, thermal, battery, or memory measurements.

## Phase 5 — Startup and UI interaction

**Goal:** Reduce measured delays without deleting work needed by the user.

- Use Perfetto to identify critical-path I/O, lock/IPC waits, expensive initializers, view inflation, synchronous audio/image operations, and repeated layout passes.
- Defer nonessential initialization until after first usable frame; keep critical study data available.
- Fix oversized/incorrect layout bounds, inset ownership, overdraw, unnecessary translucent full-screen layers, and repeated theme application only after reproducing the problem.
- Keep sound and haptic feedback; never block touch on audio decoding or a synchronous fallback.
- Measure input-to-visible-response and navigation latency at realistic refresh rates.
- Add/adjust Baseline Profiles and startup profiles only after measured journeys and stable Macrobenchmark tests exist.

**Exit gate:** Better measured startup/interaction latency with identical functional outcomes and no new lifecycle or navigation defects.

## Phase 6 — QBank database, FTS, imports, and media

**Goal:** Improve data-path efficiency while preserving correctness/durability.

- Profile query plans, cursor/materialization cost, transaction durations, FTS update/delete, and index rebuild triggers.
- Preserve incremental FTS cleanup and do not reintroduce unnecessary full-corpus rebuilds.
- Keep database access off the main thread and avoid duplicate queries on navigation.
- Keep APKG import resumable, batched, streaming, and transactional; validate media and references before final commit.
- Keep low-disk handling, safe restore/replacement, backups, and migration recovery.
- Do not extract more from QBankDb façade/orchestrator without evidence and a cohesive ownership boundary.
- Test malformed/large packages, duplicate records, low disk, cancellation, process death, and repeated import/delete/search.

**Exit gate:** Query/import/delete improvements are measurable and data integrity/regression suites pass.

## Phase 7 — Chat, HTML/WebView, and renderer

**Goal:** Reduce rendering cost while preserving rich question content.

- Preserve incremental chat append/background rendering; do not describe or regress it into full-transcript main-thread rendering.
- Add rendered-turn caching and incremental DOM updates for normal turns only after profiling; retain cached historical rendering for reconstruction/theme changes.
- Hoist reusable regular expressions and immutable parser objects out of hot paths.
- Bound input, process off-main-thread, and use plain-text fallback for ordinary renderer exceptions.
- Keep catastrophic/OOM handling only at the outer rendering boundary; do not scatter catch(Throwable) or allocate huge fallback content after OOM.
- Profile question HTML, option images, tables, zoom, links, malformed HTML/CSS, and large content.
- Preserve Chromium-first rendering and SAF permission handling.

**Exit gate:** Rich content remains correct and accessible; large/hostile content cannot cause uncontrolled allocations or UI stalls.

## Phase 8 — Ben inference, retrieval, and background work

**Goal:** Improve perceived AI speed without making AI a prerequisite for studying.

- Measure model cold/warm initialization, retrieval, reranking, generation, cancellation-release latency, and IPC separately.
- Keep deterministic local QBank/Graph-RAG retrieval authoritative; neural backends remain optional accelerators.
- Preserve isolated inference process, service-generation fencing, single-flight/latest-request-wins, exactly-once terminal state, cancellation release barrier, and hard resource governor.
- Keep a single owner for each manager domain; do not add parallel schedulers or duplicate orchestration.
- Optimize warm model reuse before changing model or introducing speculative decoding.
- Bound background concurrency and yield to foreground study; retain graceful fallback on process death/model failure.
- Do not claim a model/backend is validated without device evidence.

**Exit gate:** Lower measured latency or resource cost with cancellation, failure fallback, process recovery, and correctness tests passing.

## Phase 9 — Long-session, thermal, accessibility, and adversarial testing

Run repeated section switching, long QBank sessions, rapid next/previous, theme changes during active study, process/background/foreground cycles, low-memory/low-storage tests, corrupt image/cache tests, import interruption, large chat/HTML, malformed model responses, inference cancellation/death, and thermal/power-save scenarios.

Check talkback/content descriptions, text scaling, contrast, reduced motion, cutouts/insets, dark/AMOLED theme, and different display densities.

**Exit gate:** No reproducible crash, ANR, data loss, stuck animation, stale image/theme, or functional regression; performance remains acceptable during soak testing.

## Phase 10 — Mandatory final audit and release gate

After every phase and again before release:

1. Review exact diff and verify every claimed change is present in generated/selected source.
2. Scan all source and adjacent paths for compile/syntax/type errors, malformed XML/manifest, duplicate IDs within each individual layout, incorrect `findViewById<T>()` types, startup crash risks, lifecycle leaks, unsafe concurrency, unbounded allocations, and unsafe exception handling.
3. Scan for TODO/FIXME/placeholder/stub/not-implemented, unreachable code, uncalled classes, missing wiring, and half-implemented feature paths; repair genuine incompleteness and classify intentional markers.
4. Run unit tests, XML/static audits, architecture/ownership checks, source generation and overlay-chain checks, debug build, Android test build/instrumentation when available, release build, APK/signing/native payload checks, and the full GitHub Actions workflow.
5. Compare performance and screenshots against the recorded baseline. Reject regressions even when CI is green.
6. Fix every confirmed defect found by the audit, then rerun the affected tests and the full required gate.
7. Report commit SHA, workflow URL, exact passing/failing checks, benchmark evidence, remaining warnings, and what remains unverified.

**Release is not green unless the actual candidate's required CI workflow completes successfully.** Device-only claims must be explicitly labelled unverified until run on the target device.

## Current execution status

- [ ] Phase 0: CI recovery and trustworthy baseline
- [ ] Phase 1: performance/visual baseline
- [ ] Phase 2: wallpaper fidelity and theme identity
- [ ] Phase 3: static image pipeline
- [ ] Phase 4: live wallpaper/motion
- [ ] Phase 5: startup/UI interactions
- [ ] Phase 6: database/import/media
- [ ] Phase 7: chat/renderer
- [ ] Phase 8: Ben inference/background work
- [ ] Phase 9: soak/adversarial testing
- [ ] Phase 10: final audit and release gate

Update this checklist and attach evidence as phases complete; do not mark a phase complete on code presence alone.
