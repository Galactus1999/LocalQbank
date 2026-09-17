# Rovex v8.3.116

## Architecture stabilization: Quiz + Home

- Completed the Quiz ViewModel dependency boundary: QuizViewModel is now a plain lifecycle-agnostic ViewModel supplied by QuizViewModelFactory; Android Context/Application is kept outside the ViewModel.
- Preserved existing QuizActivity rendering, timer, persistence, answer, notes, bookmark, flashcard and Knowledge Vault behavior.
- Added MainUiState/MainViewModel for observable Home-screen state.
- Added MainRepository as the Home persistence seam for QBank metadata, resume targets, existence checks and source mutations.
- Added MainStudyUseCase for Home study-queue policy: current-QBank scoping, wrong-question selection, Today Solved, Today Revision and due counts.
- Removed direct QBankDb/ProgressStore ownership from MainActivity.
- Removed direct SharedPreferences access from MainActivity for Home UI settings.
- SourceAdapter now consumes precomputed resume targets rather than opening persistence itself.
- MainActivity now consumes MainViewModel StateFlow using lifecycle-aware repeatOnLifecycle.
- Preserved authoritative AppManagers, DashboardManager, PerformanceManager, StudyCore and ImportPipeline ownership.

## Safety

- No new parallel business-logic owner created.
- No Compose/Hilt migration introduced.
- Existing zstd, signing-certificate, APKG, SRS, backup, flashcard, themes, notes and AI safeguards remain intact.
- Android Gradle compilation remains a CI gate because this environment cannot resolve services.gradle.org.
