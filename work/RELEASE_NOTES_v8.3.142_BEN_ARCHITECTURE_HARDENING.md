# Rovex v8.3.142 — Ben Architecture Hardening

## Scope
Hardening pass following the Ben/Ren orchestration, native-runtime, lifecycle and circuit-breaker review.

## Implemented
- Added a dedicated `:inference_process` bound service for normal neural inference.
- Moved grounded EmbeddingGemma reranking and Gemma generation off the UI/main process behind Messenger IPC.
- Main process treats Binder death, binding failure and timeout as ordinary neural failure and falls back to deterministic Ben/Ren.
- Inference service skips normal `Application`/AppManagers/SQLite startup.
- Added cold-start allowance for native model initialization and a strict warm-inference timeout; a timed-out native inference terminates only the isolated inference process.
- Added persistent service-side runtime reuse for Gemma and cached EmbeddingGemma runtime reuse with a bounded idle timeout.
- Added `onTrimMemory` lifecycle eviction in the main process and inference process.
- Added explicit `trim()`/`close()` neural lifecycle handling.
- Shared the authoritative `RenCognitiveEngine` instance across AppManagers Ben/BIO construction instead of constructing parallel deterministic Ren engines there.
- Rejected neural drafts now terminate at the verifier and re-enter deterministic Ren; no rejected neural prose is returned or repaired.
- Added CLOSED/OPEN/HALF_OPEN native-backend circuit semantics with automatic half-open probing after a cooldown.
- Routed Ben Model Lab neural diagnostics/comparison and direct Gemma tests through the isolated inference process.

## Deliberately not implemented
- No deletion/merger of the existing manager ownership hierarchy.
- No fusion of Thompson bandit math into SM-2 scheduling, because that would change established SRS behavior and cross authoritative domains.
- No speculative `bindIsolatedService`/API-35 isolated-service mode; the explicit `android:process=":inference_process"` boundary is the conservative compatibility target for minSdk 26.
- No broad clinical NLU/contradiction engine.

## Build status
Android Gradle compilation remains subject to CI because the current environment may not resolve the configured Gradle distribution. This source is not declared CI-green until the actual CI build passes.
- Added percentage grounding regression tests, including punctuation immediately following `%`.
