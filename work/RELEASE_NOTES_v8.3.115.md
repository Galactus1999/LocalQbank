# Rovex v8.3.115

QuizActivity Stage 2 CI compile correction.

- Corrected stale Activity state references introduced by the v8.3.114 state-boundary extraction.
- All question position/count/test/collection references now read from QuizViewModel.state.
- Preserved QuizTimerController and QuizSessionLifecycleController boundaries.
- No new business-logic owner introduced.
- Static and source audits performed after the final change.

Build status: CI verification pending; local Gradle is blocked by services.gradle.org DNS in the current environment.
