# Rovex v8.3.115

## Quiz Stage 2 — one-and-a-half-step stabilization

- Extracted QuizActivity lifecycle persistence/checkpoint sequencing into `QuizSessionLifecycleController`.
- Kept all quiz/session business ownership in `QuizViewModel`, `QuizSessionUseCase`, repositories, StudyCore, and AppManagers.
- Moved question-position boundary policy behind `QuizNavigationUseCase` and exposed it through the existing ViewModel seam.
- Preserved Android UI rendering and event handling in QuizActivity.
- No Compose, Hilt/Dagger, or parallel business-logic owner introduced.
