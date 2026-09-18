# Rovex v8.0.5 — Stability / UX / Intelligence Patch

## Identity
- App name: Rovex
- Local assistant: Ren
- Uploaded logo crop incorporated as `res/drawable-nodpi/rovex_logo.png`, with the Q mark replaced by a clean R wordmark treatment.
- Animated `RovexWaveTextView` provides a low-cost left-to-right theme-aware wordmark wave.

## Quiz / state
- Font and header-density changes no longer recreate the quiz Activity.
- Current question position is preserved when theme changes.
- Continue now resumes a real protected/current study session instead of incorrectly opening Today's Solved.
- Due-review semantics exclude unseen questions; unseen remains a separate learning queue.
- Performance invalidation now clears derived analytics cache after progress/bookmark changes.

## Notes
- Selected explanation text is saved as the selected substring only.
- Copy remains available in the selection action mode.
- Manual note text and generated key points are separated.
- Generated notes use `[Rovex Auto Summary]` with backward compatibility for `[LocalQBank Auto Summary]`.
- Auto summaries use high-yield point extraction instead of copying the full explanation.
- Notes are grouped by subject, sorted by latest update, and support All / Recent / Key points / My notes filters.

## Flashcards
- Wrong answers no longer automatically create a flashcard after every answer.
- Explicit `MAKE FLASHCARD • ANSWER ONLY` CTA appears at the end of the explanation.
- Generated card front contains the MCQ stem/options; reveal contains only the correct option.
- Existing adaptive cards with the same front are updated in place instead of duplicated.
- Deck actions, Study Menu, SRS Settings and Stats use custom visual sheets with explicit close controls.
- Deck filter chips are explicit, focusable controls and empty states are preserved.
- Swipe navigation requires a deliberate 140dp horizontal gesture; vertical swipe no longer silently rates a card.
- Rating buttons are temporarily disabled during the save/transition to prevent accidental double taps.

## Import reliability
- Srcdoc/Marrow-style imports now track the number of embedded srcdoc documents.
- Suspicious partial srcdoc parses are not committed; the importer falls back to the general extractor.
- The uploaded Radiology Edition 8 Q-Bank regression archive contains 15 srcdoc sections and 315 questions; native fixture analysis recovered all 315.

## Ren search
- Ren now treats natural-language search such as `Find cardiology questions` as a local QBank search.
- Matching questions can be opened directly from Ren.

## Typography / headers
- Added `AdaptiveTypographyManager` for constrained UI labels, buttons, chips and headings.
- Secondary QBank/collection/flashcard headers were reduced and no longer reserve unnecessary status-bar space on immersive screens.

## Toolchain
- AGP 8.10.1
- Gradle 8.11.1 via GitHub Actions setup
- compileSdk 36 / targetSdk 36
- JDK 17

## Validation
- XML parse audit: PASS
- findViewById/XML widget-type audit: PASS
- duplicate ID audit: PASS
- AlertDialog import audit: PASS
- invalid `singleLine =` audit: PASS
- Thread.sleep/runBlocking/GlobalScope/killProcess audit: PASS
- Activity window-policy audit: PASS
- manifest class audit: PASS
- uploaded QBank regression extraction: PASS (15 sections / 315 questions)

A local Android APK compile/device test is not claimed here because the current working environment does not contain the Android SDK/Gradle distribution. GitHub Actions is configured as the authoritative compile gate.
