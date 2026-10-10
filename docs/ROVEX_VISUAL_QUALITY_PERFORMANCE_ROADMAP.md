# Rovex Visual Quality & Performance Roadmap

Status: active; Phase 0 verification is a hard gate. This roadmap is a durable implementation contract, not evidence that any phase is complete.

## Product direction

Build a distinctive, premium medical-study interface—not a generic pastel dashboard, a collection of glowing boxes, or an animation showcase that distracts from solving questions.

- Use deep charcoal/AMOLED surfaces, crisp typography, electric blue/teal accents, and clean light-theme counterparts.
- Define semantic, theme-specific tokens for backgrounds, surfaces, borders, accents, text, and interaction states. Text-flow preferences must not own card/background palettes.
- Keep ambient motion restrained, low-amplitude, and behind selected home cards. Do not animate text with arbitrary flowing gradients.
- Keep question text, options, explanations, progress, and flashcard content stable and readable.
- Use compact tactile cards with clear hierarchy. Fix oversized Home geometry in actual source/layout code, not screenshots or visual patches.
- Use selective glass/translucency only for overlays and focused controls; never apply it indiscriminately.
- Respect contrast, non-colour state cues, tap targets, accessibility, and reduced-motion preferences.
- Prioritize stability, offline-first behavior, study usability, readability, smoothness, and low resource use over novelty.

## Ordered implementation phases

### Phase 0 — Build/process verification hardening (P0; do not bypass)

1. Inspect the newest workflow, exact HEAD, failed job/step, and complete logs before edits.
2. Fix the actual root cause in tracked source, overlay, or workflow code. Keep patches small and add regression coverage.
3. Validate source-overlay selection and generated-source identity, version assertions, asset hashes/licences, dependency checks, signing-certificate fingerprint persistence, zstd Android AAR assertion, artifact upload, and benchmark-lane separation.
4. Run source and runtime-risk audits. A source-discovery pass or debug compile is not a green release workflow.
5. Repeat inspection → root-cause correction → commit → CI until the latest intended full workflow succeeds, unless a concrete external blocker is documented.

### Phase 1 — Home layout and theme foundations

- Fix oversized Home cards at the real layout/source layer.
- Introduce consistent semantic theme tokens for surfaces, text, accents, borders, and interaction states.
- Keep text-flow preferences separate from card/background palette ownership.
- Preserve corrected behavior and verify light, dark, AMOLED, and supported additional themes.
- Use editable Figma variants and actual Android screenshots as acceptance criteria.

### Phase 2 — Home card motion and imported assets

- Complete Phase 659 validation, including test discovery, exact asset hash, provenance/licence, cache cap, and rendered Android screenshot evidence.
- Evaluate legitimate, licensed Rive and Lottie assets before integration.
- Use Rive for meaningful state-driven interactions (e.g. Ben/logo idle, thinking, answering, unavailable) only after an isolated dependency/native-library risk review. Never fake loading or delay answers for animation.
- Use Lottie for appropriate decorative loops and native Android animation for small transitions.
- Pause off-screen animation; support reduced motion; measure CPU, memory, frame pacing, APK size, battery, and thermal impact.

### Phase 3 — Premium identity and interaction polish

- Add restrained signature logo/header motion and meaningful spring/morph transitions to a small number of important controls.
- Use glass surfaces selectively.
- Ensure Ben+AI overlays remain sufficiently opaque and readable in every supported theme.
- Never obstruct question solving; verify accessibility and reduced-motion behavior.

### Phase 4 — QBank and flashcard visual/data reliability

- Re-verify question solving, navigation, flashcards, bookmarks, theme transitions, import/rendering, and progress counting.
- Track the previously reported Pastel-only flashcard header/bookmark defect as a separate regression; Pastel is compatibility-only, not the design target.
- Verify actual question screens, options, explanations, bookmarks, and Ben+AI overlays with rendered screenshots.
- Preserve QBank import → SQLite commit → source exists → visible → catalog refresh → appears → opens.
- Preserve explicit deletion intent → tombstone/delete → data deletion → source disappears while unrelated sources remain.
- Crash-during-delete recovery must complete only explicitly requested deletion and preserve unrelated/legacy hidden data.
- Reimporting the same filename with different content must remain distinct.
- SQLite LIKE/ESCAPE regression must exercise the real escapeLike()/SQL path for %, _, and backslash. Never claim the source.file_name UNIQUE constraint was removed without a real migration.

### Phase 5 — Performance and runtime validation

- Measure startup, scrolling/jank/frame pacing, memory, battery, APK size, and animation cost against a recorded baseline.
- Avoid UI-thread-heavy work and per-frame allocation/recolouring of large assets.
- Inspect Rive runtime dependency tree, Kotlin/AGP/minSdk compatibility, supported ABIs, native libraries, libc++_shared.so collisions, and offline playback.
- Keep AI/animation work off critical study paths; preserve deterministic fallback and foreground responsiveness.

### Phase 6 — Comprehensive release audit

- Scan the whole project for TODO/unwired code, duplicate logic, compile/resource errors, dependency/API mismatches, broken tests, and regressions.
- Parse every XML layout. Duplicate IDs are errors only within the same XML file; reusing an ID across separate layouts is legal.
- Audit each findViewById<T>() against the actual XML widget type.
- Audit Activity onCreate/startup paths for ClassCastException, RuntimeException, ANR, OOM, unsafe catch(Throwable), and lifecycle hazards.
- Verify source overlays, version assertions, signing certificate fingerprint checks, zstd Android AAR assertions, APK/package integrity, and uploaded artifacts.
- Provide an updated source ZIP only when its exact path exists and its integrity/hash has been checked.

## Architecture and safety invariants

- AppManagers owns engines/managers. PerformanceManager, AdaptiveEngineManager, ResilienceManager, EngineHealthManager, EngineMeshCoordinator, IntelligenceOrchestrator, StudyCore, ImportPipelineCoordinator, and other existing authoritative managers remain authoritative.
- RovexBatteryManager is a policy/governor only: observe battery/charging, power-save, and thermal state; do not own scheduling/business logic, hold wake locks, alter system settings, or block foreground study.
- Do not introduce parallel business-logic owners or shadow managers.
- Preserve backups, SRS, flashcards, themes, notes, WebView/QBank image import, APKG importer, zstd Android AAR, Macrobenchmark/JankStats, signing validation, and all corrected features.
- Renderer: bounded input, background rendering, normal Exception → plain-text fallback, catastrophic/OOM handling only at the outer rendering boundary. Do not allocate large fallback content after OOM or scatter catch(Throwable).
- Search-index rebuild hardening should add batching, generation safety, observable progress, cancellation, and concurrency protection. StreakManager explicit reschedule work is lower priority and must not be mixed into risky QBank repairs without explicit scope.

## Asset provenance and licensing

For every imported asset, record source URL, author/source, licence and attribution requirements, download hash, file size, format, and redistribution permission in THIRD_PARTY_ASSETS.md and the visual asset manifest. Recheck the current licence before redistribution. Do not claim an asset is Rive/Lottie unless a real compatible asset is bundled, loaded, and rendered by the actual UI. Bundle approved assets for offline use and provide a safe fallback.

The current Phase 659 Lottie asset candidate is documented in the continuation handoff; verify the live asset page/licence and exact SHA-256 before treating it as approved.

## Proof and acceptance rules

Every meaningful phase report must state:
1. Root cause or objective.
2. Exact files/overlays/workflows changed.
3. What is actually implemented.
4. Regression tests/static checks run.
5. Workflow URL, exact commit SHA, and status of each relevant stage.
6. Screenshot source and what it proves; if absent, say **“no actual Android screenshot verified.”**
7. Whole-project scan findings and remaining issues.
8. Verified source artifact link/hash if available.
9. Next phase and why it is safe.

Never call a running/cancelled workflow green, source discovery a compile pass, compile a test pass, Figma a live app screenshot, a mock an implemented feature, or CI emulator testing device testing. Never claim success without evidence.
