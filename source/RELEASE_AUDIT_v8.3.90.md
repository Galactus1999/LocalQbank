# Rovex v8.3.90 release audit

## Source baseline
- Package: `com.localqbank.library`
- Version: `8.3.90`
- versionCode: `188`
- Baseline: v8.3.89 source with CI-reported compile failures corrected.

## Compile corrections
- `BenExamTerminology.Context` renamed to `ExamContext`; Android `Context` is now unambiguous.
- Alias map explicitly qualifies the outer `entries` property and entry type.
- `RenActivity` uses `android.widget.FrameLayout.LayoutParams` for the child of `ScrollView`.
- `RenCognitiveEngine` visual concept iteration uses an explicit `concept` lambda parameter.

## Static audit
- XML and source scans performed after edits.
- Per-layout duplicate IDs checked.
- Unsafe blocking patterns and broad `catch(Throwable)` checked.
- Version/CI assertions synchronized to 8.3.90 / 188.
- Visual retrieval hard image constraint retained.
- No neural model binaries bundled.

## Build status
Android Gradle compilation remains subject to the CI build gate. Local compilation must not be described as green unless Gradle compilation actually completes successfully.
