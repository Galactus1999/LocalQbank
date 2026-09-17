# Rovex v8.3.108 — Architecture Stabilization / Quiz Refactor

This release continues the architecture stabilization program.

- Added focused Quiz navigation/session, notes, bookmark, and checkpoint use cases.
- Extended QuizViewModel as a presentation/application boundary without moving the entire Activity into it.
- QuizActivity now accesses quiz persistence through QuizViewModel seams rather than direct QBankDb/ProgressRepository calls.
- Preserved StudyCore, StudyStateRepository, QBankDb, ProgressRepository and AppManagers as authoritative owners.
- No Compose migration or Hilt/Dagger migration was introduced.
- Static audit completed; Android Gradle compilation remains unverified because services.gradle.org DNS is unavailable in the current environment.

Next milestone: finish QuizActivity state extraction and make the Activity substantially presentation-only before starting MainActivity.
