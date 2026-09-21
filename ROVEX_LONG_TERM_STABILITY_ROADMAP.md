# Rovex Long-Term Stability & Performance Roadmap

## Baseline
- Current repair baseline: v8.3.295.
- Stability is the release gate: no feature work is considered complete until source audit, tests, release build, signing, APK verification, native payload verification, and CI are successful.
- Preserve authoritative AppManagers and existing offline-first/deterministic fallback architecture.
- Do not create competing business-logic owners.

## Phase 1 — AMOLED rendering quarantine
Goal: make imported QBank HTML unable to control final text/background colors.

- Introduce semantic rendering roles: QUESTION, OPTION, OPTION_LABEL, EXPLANATION, AI, CORRECT, WRONG, MUTED, TABLE_HEADER, TABLE_CELL.
- Imported HTML/CSS may preserve structure and emphasis but never own final foreground/background colors.
- Centralize final colors in the theme renderer.
- Add ThemeSafeTextView/role contract where practical.
- Add diagnostic assertions for AMOLED effective text color and residual color/background spans.
- Torture-test legacy font colors, inline CSS, nested spans, tables, theme switching, next/previous navigation, and lifecycle transitions.
- AMOLED is not considered fixed until the actual offending render path is observable and the invariant holds.

## Phase 2 — Frankenstein response/presentation separation
- Keep Ben's internal response structured rather than HTML-first.
- Represent paragraphs, tables, lists, warnings, formulas, code, and citations as structured blocks.
- Serialize to Markdown only at the presentation boundary.
- Keep renderer bounded and resilient to malformed model output.
- Keep WebView constrained and explicitly destroyed when no longer needed.

## Phase 3 — Ben latency architecture
- L0 ephemeral request state.
- L1 session state.
- L2 durable learner/concept state.
- Exact/recent-query cache before retrieval.
- SQLite FTS/concept retrieval before neural inference.
- EmbeddingGemma remains an optional accelerator/reranker, never a prerequisite for simple search.
- Introduce request latency budgets and fail-fast/best-available behavior.
- Maintain single-flight/latest-request-wins and hard resource governor.

## Phase 4 — Database/import durability
- Stage imports before production commit.
- Validate counts, references, media, and schema before commit.
- Atomic file replacement for backups/restores.
- Crash-safe migration journal.
- Database health metadata and targeted integrity checks.
- Never delete the live database before a verified replacement exists.
- Keep APKG import resumability and transactional guarantees.

## Phase 5 — Section-switch architecture
- Persistent application shell and footer.
- Lazy section controllers.
- Cache cheap summaries.
- Defer expensive aggregation/DB/HTML/image work.
- No large View trees or synchronous database aggregation during navigation.
- Measure section-switch latency and frame timing before architectural changes.

## Phase 6 — Performance engineering
Critical journeys:
- cold launch
- Home -> QBank
- QBank -> subject
- subject -> test
- next/previous question
- Home -> Cards
- Home -> Mastery
- Frankenstein open/search

Measure TTID/TTFD, frame timing, jank, allocations, GC pauses, DB latency, WebView memory, and AI latency. Add Baseline Profile only after measurements establish the real hot paths.

## Phase 7 — Red-team / soak testing
Database:
- process kill during import/backup/restore
- malformed APKG
- duplicate data
- low disk space
- large media

UI:
- repeated section switches
- repeated next/previous
- theme switching
- lifecycle/background/foreground
- long sessions

Ben:
- duplicate requests
- cancellation
- timeout
- process death
- neural backend failure
- malformed/huge responses

Renderer:
- malformed HTML
- hostile CSS/color spans
- huge tables
- nested formatting
- AMOLED contrast regression

## Release discipline
- Small auditable repair batches.
- Inspect exact changed source before CI.
- Run static/XML/type/lifecycle/runtime-risk audits.
- Compile locally where possible.
- Run the complete GitHub Actions release workflow.
- Inspect exact failed step/log before any new patch.
- Never call a build green without a successful complete workflow.
- Preserve signing certificate, Firebase SHA-1, Zstd/native payload, backups, SRS, flashcards, notes, importer, WebView/QBank images, and existing corrected features.
