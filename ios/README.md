# Rovex for iOS — native foundation

This directory starts a **separate native SwiftUI iOS app** for Rovex. It does not modify Android application sources, package identity, signing, Gradle configuration, or release behavior.

## Current implementation

- Native SwiftUI tab navigation and responsive layouts for iPhone and iPad.
- Six theme palettes: AMOLED, Light, Pastel, Mint, Sunset, and Lavender.
- Local QBank JSON import, question navigation, answer selection, answer reveal, and explanation display.
- Phase 4 import compatibility for common field aliases (`questionText`/`stem`, `rationale`, answer labels A–D), keyed options, and wrapper arrays (`questions`, `items`, `data`, `results`).
- Locally persisted flashcards with create, flip, navigate, delete, due counts, and Again/Hard/Good/Easy review ratings using an offline interval scheduler.
- Versioned study-data archive import/export covering both questions and flashcards, with duplicate filtering and fresh local IDs.
- One atomic archive file for the current question/flashcard snapshot; first-run migration from the initial two-file iOS layout.
- Atomic JSON writes in the app's Application Support directory.
- Import validation and duplicate question-stem filtering.
- A transparent Ben status screen: the Android Ben engine, model runtime, retrieval index, and neural inference are **not** ported in this foundation.
- A macOS GitHub Actions workflow that builds Debug and Release for the iOS Simulator without signing.

## Build

Requirements: macOS with Xcode 16 or newer.

1. Open `ios/Rovex.xcodeproj` in Xcode.
2. Select the `Rovex` scheme and an iOS Simulator.
3. Build and run.

The GitHub Actions workflow `.github/workflows/ios-ci.yml` runs both Debug and Release simulator builds. A successful source commit or static review is not a substitute for a completed green Xcode workflow. An App Store distribution build still requires Apple signing and provisioning.

## QBank JSON format

Import a UTF-8 JSON file using either a top-level question array, an object with a `questions` array, or a Rovex study archive exported from Settings. Each question uses a zero-based `correctAnswerIndex`. Export/import from Settings transfers questions and flashcards together.

```json
{
  "questions": [
    {
      "question": "Which chamber pumps blood into the systemic circulation?",
      "options": ["Right atrium", "Right ventricle", "Left atrium", "Left ventricle"],
      "correctAnswerIndex": 3,
      "explanation": "The left ventricle ejects oxygenated blood into the aorta."
    }
  ]
}
```

The `id` and `explanation` fields are optional. The question stem, at least two options, and a valid correct-answer index are required. Canonical `correctAnswerIndex`/`answerIndex` integers remain zero-based; string answer labels A–D are mapped to their corresponding option. An unrecognized answer is marked invalid and rejected by import validation rather than guessed. The importer accepts question arrays or wrapper keys `questions`, `items`, `data`, or `results`; question text aliases include `questionText`/`stem`/`text`, explanation aliases include `rationale`, and options may be arrays, keyed objects, or `optionA`–`optionD` fields. These are JSON schema compatibility conveniences, not proof of direct Android SQLite or APKG compatibility. Import is local; duplicates by normalized question stem are skipped.

The portable Rovex archive uses `schemaVersion: 1` and contains `exportedAt`, `questions`, and `flashcards`. The importer merges records instead of replacing existing data. Flashcards are deduplicated by normalized front/back content. Imported IDs are regenerated to avoid identifier collisions. Unsupported future archive versions are rejected rather than silently misread.

## Data and parity boundaries

- iOS data currently stays inside the iOS app sandbox. It does **not** automatically synchronize with the Android app.
- Existing Android SQLite databases, HTML question rendering, images/attachments, APKG import, Android SRS settings and limits, backups, online services, and the Ben AI architecture are not yet migrated. The iOS flashcard scheduler is an initial independent implementation; it is not yet behaviorally equivalent to Android SRS.
- The Android implementation remains authoritative for the existing Android release. iOS work is isolated under `ios/` plus its dedicated CI workflow.
- Keep feature parity work incremental: build/test each subsystem, define a cross-platform data format and migration/import/export path, and do not claim iOS parity until the corresponding behavior is implemented and tested on simulator and physical devices.
