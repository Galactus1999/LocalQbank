# Rovex v8.3.111 — QuizActivity Architecture Stabilization

## Stage 2 progress
- Strengthened the QuizActivity state boundary: session state is now read-only from the Activity and mutations are routed through QuizViewModel.
- Moved quiz cursor transitions, position persistence, session cursor persistence, checkpointing, and elapsed-time persistence behind QuizViewModel APIs.
- Removed direct SharedPreferences access from QuizActivity behind `QuizUiPreferences`.
- Moved quiz-triggered flashcard and Knowledge Vault actions behind focused use cases exposed by QuizViewModel.
- Preserved StudyCore/AppManagers as authoritative owners; no parallel business-logic engine was introduced.
- Preserved HTML, table, image, fullscreen-image, and Android UI rendering in QuizActivity.

## Stability intent
This is an incremental architecture refactor. Existing quiz behavior is preserved; no Compose/Hilt migration is introduced.

Build status: not claimed CI-green until CI actually passes.
