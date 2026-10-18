# Rovex Security, Stability & Product Roadmap
**Last updated:** 2026-10-18  
**Repository:** `Galactus1999/LocalQbank`  
**Application ID:** `com.localqbank.library`  
**Latest working artifact mentioned in chat:** v8.3.692 / versionCode 778. This is not a verified-green build; source checks passed but full Gradle/CI/device verification remains pending.

> This document supersedes the previous long-term stability roadmap. Treat older roadmap phases as historical context only unless explicitly carried forward below. Findings below are source-audit findings reported for v8.3.687 and require fresh validation against the exact current source before being called fixed or still present.

## Non-negotiable engineering and verification rules
- Security, data integrity, zero crashes/ANRs, foreground responsiveness, offline-first operation and deterministic fallback take priority over feature velocity.
- Keep `AppManagers` and existing authoritative engines as owners; do not introduce parallel business-logic owners.
- Never claim a repair, compile, test, CI, signing or device verification without evidence from the exact source/artifact. Report STATIC REVIEW, STATIC AUDIT, UNIT TEST, INSTRUMENTED TEST, BUILD, CI, DEVICE and BLOCKED separately.
- Every patch needs a diff-scoped regression matrix, tests for historical contracts and new behavior, a whole-source/runtime-risk audit, and explicit unverified items.
- Never silently replace live QBank/database data after failed, empty or partial import/restore. Stage, validate and commit atomically.
- Preserve package ID, monotonically increasing versionCode, signing certificate, Zstandard Android AAR/native payload, backup/SRS/flashcard/notes behavior, and established importer/renderer protections.
- Keep source archive hygiene under review; generated audit files, caches and `__pycache__` should not be packaged accidentally.

## Phase 0 — P0 security and build supply-chain hardening
### A1. Firestore friend-code ownership
Audit `firestore.rules` around the reported line 66. Separate create and update rules. A user must not overwrite another user's `friendCodes/{code}`; updates must bind the existing document owner to `request.auth.uid`. Creation must reject an already-existing document and validate allowed fields. Add rules tests for unauthenticated access, owner update, cross-user overwrite, duplicate create and malformed payloads.

### A2. XP event integrity and client authority
Audit `functions/index.js` and all client writes to XP/counter documents. Preserve existing transaction/idempotency guards (the v8.3.687 report notes `processedAt` and a transaction already exist); do not discard them or assume they are sufficient. Establish deterministic event IDs, server-side authorization/validation, per-user abuse/rate limits, replay protection, and deny direct client writes to server-authoritative counters. Add emulator/function tests for replay, concurrent duplicate events, forged events, rate abuse and legitimate awards. Do not trust client-reported score/XP values.

### A3. R8 release configuration
Explicitly inspect release `isMinifyEnabled` and `isShrinkResources`. Enable R8/resource shrinking only after auditing reflection, JNI, serialization, WebView/extractor interfaces and model/runtime loading. Add targeted keep rules and compare release APK behavior, manifest, native libraries and certificate against expected values. No blanket keep-all rules or untested shrinker change.

### A4. Untrusted HTML/WebView import boundary
Re-audit exported status, file access, JavaScript and bridge use in `HtmlImportActivity`. Keep untrusted HTML isolated from privileged native APIs; prefer `WebViewAssetLoader` and origin-restricted messaging when compatible with the extractor. Validate the actual navigation origin and external intents. Any change must preserve import compatibility and the single-worker/coordinator contract. Add hostile HTML/JS, file URL, navigation, renderer-death and regression fixtures.

### Build supply-chain integrity
Audit `tools/prepare_litert_qualcomm_v75_jit.sh` and all tasks wired to `preBuild`. The reported risk is downloading a 2.35 GB archive without SHA-256 verification and executing a script from that archive. Pin an independently trusted digest/signature and verify before extraction; never execute untrusted scripts bundled in downloads. Prefer a controlled, immutable artifact repository with provenance. Make optional Qualcomm/SM8650 v75 runtime acquisition explicit and reproducible; do not make every default build depend on a huge vendor download. Consider on-demand/user-installed runtime packaging only after measuring size, compatibility, licensing and delivery constraints. Default inference must safely fall back to CPU/GPU and remain optional.

**Phase 0 acceptance gate:** Firestore rules tests and Functions emulator tests pass; R8 release APK builds and runs; import security tests pass; archive provenance/hash is independently verified; exact complete GitHub Actions release workflow passes; APK signing fingerprint and required Zstd native payload are inspected. Until then, findings remain open.

## Phase 1 — Backup, restore and APKG safety
### Backup compatibility
Reported concern: `QBankDb.snapshotTo` and `FlashcardDb.snapshotTo` rely on `VACUUM INTO`, which may not exist on every supported SQLite version. Verify against the app's actual Android/SQLite matrix, then implement a tested fallback (SQLite backup API where available, or a carefully checkpointed consistent copy). Add instrumented tests on API 26 and current Android, including WAL, concurrent writes, low storage and interrupted backup.

### Restore lifecycle and recoverability
Reported concern: `BackupManager.applyPendingFullRestore()` copies databases during `Application.onCreate` and swallows failures. Move expensive restore work out of main-thread startup into a staged/resumable flow. Use a journal/marker, validate snapshot integrity/schema before swapping, atomic replacement with rollback, and a user-visible recoverable failure state. Never leave a half-restored pair of databases.

### APKG decompression/media safety
Add hard per-file and aggregate decompressed-byte caps around ZIP/Zstandard streams; count actual bytes written rather than trusting outer ZIP metadata for nested compressed data. Check free space against measured/staged output. Prevent path traversal and filename collisions: media identity should preserve deterministic mapping (e.g. safe normalized path plus content hash/index) rather than flattening all names to basename. Test Zstd bombs, malformed archives, duplicate basenames, low disk, cancellation and partial extraction. Preserve `com.github.luben:zstd-jni:1.5.7-16@aar` and verify the APK contains the expected arm64 library.

### Backup confidentiality/authenticity
Current report says SHA-256 is stored inside the backup and backups may be published through MediaStore. A digest alone does not authenticate a backup against an attacker who can rewrite both content and digest. Design optional authenticated encryption (AES-GCM; passphrase KDF selected after Android-compatible security review) and/or signatures with explicit key management and recovery UX. Keep cloud backup minimal and never claim authenticity from an embedded hash alone.

## Phase 2 — Import durability and universal-format regression
- Compare current import source against v8.3.299 when its archive can be materialized; do not claim a direct diff until then.
- Build a provider fixture suite: DAMS assignment variants, Marrow `srcdoc`, nested object-root question arrays, aliases, multiple assignment boundaries, 60–180 MB HTML, unknown-size SAF/cloud providers, malformed/empty input and repeated imports.
- Compare expected section/question/media counts against imported counts. Record per-stage diagnostics with bounded memory use.
- Preserve import transaction invariants: stage → parse → validate → commit → source/catalog refresh → open. Failed/zero-question parsing must preserve the existing QBank. Same filename with different content must remain distinct. Deletion must affect only explicitly selected data and recover safely after process death.
- Test cancellation, timeout, renderer termination, concurrent requests, provider streams and low storage. Streaming parser is preferred for recognized large formats; compatibility fallback must be bounded.

## Phase 3 — Mock exam scoring and planner
- Replace percentage-only scoring with an explicit `ScoringScheme` model. Implement only verified exam marking rules (including NEET-PG +4/−1 if confirmed for the target exam/version); do not imply that every exam uses the same scheme.
- Show correct, wrong, unanswered and optionally guessed/time-pressure breakdown. Rank-band estimates must be labeled estimates and supported by data, not presented as a prediction guarantee.
- Wire `LocalMockExamPlanner` into the live exam flow only after validating its blueprint, subject weightage, question count, duration, navigation and answer persistence. Do not delete it as “dead code” until call graph and product behavior are verified.
- Add scoring, pause/resume, timer, navigation, process-death and answer-submission tests.

## Phase 4 — Ben learner model, retrieval and review scheduling
- Replace raw `correct/seen` mastery with a per-concept Beta posterior or another calibrated, uncertainty-aware model. Update from durable attempt history and deduplicate attempts; do not count repeated observations of the same attempt. Apply recency decay/half-life and show uncertainty.
- Move repeated due-date logic into one authoritative scheduler only after inventorying all existing QBank/SRS/flashcard behavior. Evaluate FSRS migration via a versioned migration and shadow comparison; never silently change intervals or discard review history.
- Replace O(N) in-memory scans with indexed SQL/retrieval queries where measurements show they are a bottleneck. Rank by evidence, recency and retrievability; distinguish “recently wrong” from “ever wrong.”
- Do not turn broad `runCatching` fallbacks into silent success. Centralize diagnostics/reporting and preserve cancellation semantics. Add lint/source checks gradually rather than banning patterns without migration.

## Phase 5 — Medical-answer evidence and user safety
- Surface a compact evidence state on Ben answers: **verified**, **partial evidence**, or **unsupported**, with links to retrieved source questions/references where available.
- Clearly distinguish retrieved evidence from model inference. Unsupported or conflicting medical answers must not be presented as verified. Keep retrieved content treated as untrusted data.
- Preserve verifier-first grounding and deterministic local fallback. Neural inference must remain resource-governed, cancellation-safe and unable to block foreground study. Keep emergency stop and model/runtime telemetry in Adaptive Engine settings.

## Phase 6 — Accessibility, localization and resilient UI
- Inventory hardcoded strings and Toasts; extract incrementally into Android resources and add localization-ready plural/format handling.
- Audit semantic labels, focus order, keyboard/switch access, contrast, text scaling and dynamic type across code-built and XML UI. Add accessibility checks to CI where practical.
- Respect system reduce-motion settings and app-level motion controls; animator scale alone may not represent all accessibility preferences.
- Keep the visual redesign incremental: one theme → one screen → one component family → source/static audit → CI → device screenshot → functional/performance checks. Home graph should be legible and proportionate, use real saved data, and handle sparse/no history honestly.

## Phase 7 — Streaks, planning and study coaching
- Use a configurable study-day boundary (e.g. 04:00) with a documented timezone policy; test travel/timezone changes before altering streak behavior. Streak freeze/rest-day tokens are optional product features, not prerequisites for correctness.
- **Exam Command Center:** configurable exam date, days remaining, projected score with uncertainty, backward-planned daily load, and a weekly rebalanced plan.
- **Ben Orb:** in-app draggable assistant (no overlay permission), quiet idle state, opt-in context-aware nudges, never obstructing timed exams.
- **Smart study nudges:** event-triggered and capped per day; learn dismissals without spam.
- **Error DNA:** categorize misses (knowledge gap, misread, careless, time pressure) and show weekly patterns based on actual data.
- **Marks-per-minute coach:** detect time spent well above a user's median and offer skip/return guidance without interrupting a timed exam.
- **Retention heatmap / rank-gap meter:** subject-level retention and marks gap, with transparent assumptions.
- **Interleaved boss rounds:** mixed-subject sets built from evidenced weak concepts.
- **Voice revision:** on-device TTS and hands-free recall with explicit privacy and audio controls.
- **Study-buddy duels:** server-authoritative scoring and replay protection must land before competitive XP features.
- **Burnout guard:** optional, privacy-preserving session-pattern prompts; avoid diagnosing health conditions.
- **Weekly Dr. Ben report:** one-page changes, decaying concepts and next actions.

## Phase 8 — Architecture and performance
- Move gradually toward repositories/ViewModels/StateFlow and explicit dependencies without a risky rewrite of authoritative managers.
- Add database schema/migration tests; inventory raw SQL and global singleton use.
- Measure cold launch, Home→QBank, subject→test, next/previous question, flashcards, mastery, Frankenstein search, import, backup/restore, and Ben latency. Track TTID/TTFD, frame timing/jank, allocations/GC, DB latency, WebView memory and AI latency.
- Optimize measured hot paths, then add Baseline Profiles where justified.
- Red-team/soak test process death during import/backup/restore, malformed APKG, duplicate content, low disk, repeated navigation, theme/lifecycle changes, long sessions, Ben cancellation/timeouts/process death and hostile/huge HTML.
- Keep production source archives clean; put reports in a documented `docs/` directory and exclude caches/generated artifacts.

## Reported audit findings that require revalidation
The 2026-10-18 v8.3.687 review was explicitly static-only (324 Kotlin files, about 44K lines; no compile or runtime). It reported these findings:
1. `firestore.rules:66` combined create/update permissions for friend codes.
2. XP Cloud Function trust/idempotency/rate-limit/client-counter authorization risk (transaction and `processedAt` guard were also noted; inspect before changing).
3. Release R8 minification/resource shrinking not explicitly enabled.
4. Exported HTML import/WebView JavaScript/file-access boundary.
5. Unverified Qualcomm archive download and embedded-script execution in `preBuild`.
6. SM8650-only vendor runtime packaging/size concern.
7. `VACUUM INTO` compatibility on older SQLite.
8. Main-thread pending restore with swallowed failure.
9. APKG decompression caps and media basename collision.
10. Backup confidentiality/authenticity gap.
11. Percentage-only mock scoring / negative-marking gap.
12. Mock planner appears disconnected from live Exam Mode.
13. StudyStrategyEngine scan/ranking/recency and duplicated scheduling logic.
14. BenLearnerModel mastery double-counting/calibration risk.
15. QBank and flashcard schedulers may diverge.
16. Broad swallowed exceptions can mask domain failures.
17. Localization/accessibility/reduce-motion gaps.
18. No user-visible Ben evidence/confidence state.
19. Streak calculation uses system-zone `LocalDate.now()`.
20. Architecture/test coverage/repository hygiene debt.

These are **reported findings, not all independently confirmed facts**. Re-open each exact file and run relevant tests before marking it OPEN, FIXED or INVALIDATED. The previous roadmap's AMOLED renderer, structured Ben response, latency architecture and performance/soak-test work remains relevant only where it supports the phases above; it is no longer the controlling phase order.

## Required delivery report for every patch
1. Exact base commit/source ZIP and SHA-256.
2. Exact files changed and reason.
3. Root-cause evidence, not just symptom patch.
4. Diff-scoped regression matrix and test results.
5. Static audit, unit, instrumented, build, CI, device, signing and native-payload status separately.
6. Any blocked or unexecuted verification, stated plainly.
7. New source ZIP and checksum when requested; never call it green unless the complete release workflow succeeds.
