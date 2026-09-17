# Rovex v8.3.211 — Two-Phase User-Readiness Reform

## Phase 1 — durability/crash containment
- Flashcard study cursor, reveal state, studied count and elapsed time are persisted as a compact lifecycle checkpoint.
- Meaningful flashcard transitions use synchronous SharedPreferences commit so the checkpoint is durable before returning from the transition.
- Existing SQLite SRS transaction remains authoritative for review results.
- Existing isolated Ben process, deterministic fallback, cancellation barrier and resource governor remain unchanged.

## Phase 2 — interface/animation quality
- Central AnimationPolicy respects Android animator/transition scale settings.
- Infinite decorative animations stop automatically when Android animations are disabled.
- Flashcard reviewer keeps the existing 48dp controls, swipe navigation, full-screen image path and renderer-crash containment.
- No Compose rewrite or competing state owner was introduced; this preserves the current production architecture while reducing regression surface.

## Verification policy
- Whole-project source scan.
- XML parse + per-layout duplicate-ID scan.
- Dangerous blocking/concurrency pattern scan.
- Gradle compile attempted; if Gradle distribution cannot be downloaded, Android compilation remains explicitly unverified.
