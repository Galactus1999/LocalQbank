# Architecture Stabilization v8.3.116

## Scope

This release advances Stage 2 (QuizActivity) and Stage 3 (MainActivity) without replacing existing authoritative engines/managers.

## QuizActivity

- QuizViewModel is now a plain `androidx.lifecycle.ViewModel`.
- Android `Context`/`Application` dependencies are supplied through `QuizViewModelFactory` and are not retained by the ViewModel.
- Existing QuizSessionRepository, focused use cases, timer controller, lifecycle controller and StudyCore/AppManagers ownership are preserved.
- No direct QBankDb/ProgressStore/SharedPreferences construction remains in QuizActivity.
- Activity remains responsible for Android UI rendering, WebView/media rendering and Android event handling.

## MainActivity

- Added `MainUiState` and `MainViewModel` using immutable StateFlow exposure.
- Added `MainRepository` for QBank persistence seams and resume metadata.
- Added `MainStudyUseCase` for queue/collection policy.
- MainActivity no longer directly constructs QBankDb or ProgressStore.
- MainActivity no longer directly accesses SharedPreferences.
- SourceAdapter consumes precomputed resume targets and no longer owns persistence.
- MainActivity collects state with `repeatOnLifecycle(STARTED)`.
- DashboardManager remains the authoritative dashboard/application manager.
- PerformanceManager remains authoritative for progress snapshots.

## Validation

- XML parsed: 27 files.
- Duplicate IDs: none within any individual layout.
- Typed MainActivity `findViewById` audit: 63 calls; 0 real XML/widget type mismatches.
- Direct QBankDb in MainActivity/QuizActivity: 0.
- Direct ProgressStore in MainActivity/QuizActivity: 0.
- Direct SharedPreferences in MainActivity/QuizActivity: 0.
- `runBlocking`: 0.
- `GlobalScope`: 0.
- `Thread.sleep`: 0.
- active `catch(Throwable)`: 0.
- unsafe `PRAGMA foreign_keys=ON`: 0.
- version: 8.3.116 / versionCode 214.
- ZIP integrity: validated after packaging.

## Compilation gate

A local Gradle compile was attempted after the final source changes. The Gradle wrapper could not download Gradle 9.3.1 because DNS resolution for `services.gradle.org` is unavailable in the current execution environment. This is an environment limitation; Android compilation is therefore **not claimed green**. GitHub CI remains authoritative.
