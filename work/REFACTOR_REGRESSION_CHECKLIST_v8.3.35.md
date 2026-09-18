# Rovex v8.3.35 Manual Regression Checklist

Run after each incremental refactor slice. Do not alter XML layout IDs or theme resource files during logic-only refactors.

## Core solving
- Launch app from a clean process.
- Open a main QBank.
- Open an in-progress sub-QBank and confirm its highlight is visible.
- Open a question and answer correctly.
- Answer incorrectly and verify progress updates once.
- Navigate next/previous without duplicate or skipped questions.
- Verify continuous question/flashcard counts around 99 → 100 → 101.

## SRS
- Verify a correct answer produces the expected interval.
- Verify an incorrect answer resets according to the existing scheduler rules.
- Verify ease/repetition values persist after leaving and reopening the QBank.
- Verify daily limits and today-only overrides remain enforced.

## Adaptive engine
- Confirm normal solving remains responsive.
- Confirm adaptive changes stay bounded to existing performance knobs.
- Confirm safe mode can activate under constrained conditions and recover normally.
- Confirm disabling autonomy does not block foreground studying.

## Flashcards
- Open flashcards and reveal an answer.
- Move Mark and Bookmark independently and confirm positions persist.
- Verify All / Manual / Imported selection state.
- Import a small APKG and confirm cards appear only after successful commit.

## Notes
- Save text-only note.
- Save image + written note and confirm they remain associated.
- Long-press note → Go to Question and verify exact owning question opens.

## Theme/UI smoke test
- Light
- Dark
- AMOLED
- Midnight Blue
- Sepia

For each: main QBank names readable, dialogs/action sheets readable, selected states visible, no default dark popup appears where rich theme-aware UI is expected.
