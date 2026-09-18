# Rovex v8.3.112

## Quiz Stage 2 — half-step
- Extracted Android CountDownTimer lifecycle mechanics from QuizActivity into QuizTimerController.
- QuizTimerController is presentation-only and does not own quiz/session state.
- Exam remaining time continues to be owned by QuizViewModel/QuizUiState.
- Preserved existing timer behavior, submission behavior, and guidance timer behavior.
- No changes to StudyCore/AppManagers ownership.
