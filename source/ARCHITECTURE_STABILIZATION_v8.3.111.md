# Rovex Architecture Stabilization — v8.3.111

## Stage 2 — QuizActivity

This slice strengthens the presentation boundary without rewriting authoritative engines.

### Activity responsibilities
- Android lifecycle and event handling
- View construction/rendering
- HTML/table/image rendering
- dialogs/popups and visual feedback
- timers and gesture handling

### ViewModel responsibilities
- Quiz session state
- Session resolution/configuration
- Position transitions and persistence
- Session cursor/checkpoint persistence
- Elapsed-time persistence
- Answer/notes/bookmark/mistake operations
- Quiz-triggered flashcard and Knowledge Vault use cases

### Explicitly preserved
- StudyCore and AppManagers remain authoritative.
- No parallel database owner was introduced.
- No Hilt/Dagger or Compose migration.
- Existing import, SRS, flashcard, notes, theme, image, Ben, zstd, signing, and CI protections remain in place.

### Remaining Stage 2 work
- Reduce remaining Activity coordination where it is safe to do so.
- Add load-bearing Quiz/ViewModel tests and Android instrumentation before declaring Stage 2 complete.


## v8.3.111 compile correction
The v8.3.110 CI log exposed two assignments to the read-only `examRemainingMs` Activity alias. These were moved behind `QuizViewModel.setExamRemainingMs()`.
