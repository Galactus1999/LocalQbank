# Rovex v8.3.246 — CI DNA compile-fix

This release is a surgical correction of the v8.3.245 CI compile failure reported in `logs_95522446312.zip`.

## Defects treated

1. `QuizActivity.kt:1728` — `ContextPackBuilder` was referenced without importing `com.localqbank.library.ai.context.ContextPackBuilder`.
2. `ai/context/EvidencePack.kt:31` — a public extension exposed the internal `BenContrastiveEvidence` receiver. The adapter is now `internal`, matching the receiver visibility and preserving the package-private Phase-3 evidence boundary.
3. `exam/MockExamPolicy.kt:20` — `range.first` / `range.last` were used as properties on a `List<Int>` returned by `chunked()`. They are now invoked as `range.first()` / `range.last()`.

No feature behavior was intentionally changed. Version bumped monotonically to 8.3.246 / versionCode 340.

## Validation

- XML parsing: run after modification
- static/ruthless audit: run after modification
- architecture regression audit: run after modification
- source ZIP integrity: run after modification
- Android Gradle compilation: must be confirmed by CI; do not infer green status from static checks.
