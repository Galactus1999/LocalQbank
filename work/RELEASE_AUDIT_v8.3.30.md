# Rovex v8.3.30 — Flashcard Pastel Theme & Rich Dark-Text Audit

## Scope
Focused visual change to the Flashcard library surface and its study/tools sheets.
Existing flashcard actions, deck filtering, SRS behavior, APKG import behavior, database operations,
and navigation contracts are unchanged.

## UX changes
- Flashcard action board now uses the same soft pastel visual language as the Home/Collections cards.
- Pastel palette includes sky blue, light pink, light red, lavender and light yellow.
- Flashcard header changed from the previous blue/green/brown semantic palette to a restrained sky-blue/lavender gradient.
- Review Due, Bookmarked and Study use compact pastel tiles.
- More tools uses a compact pastel control.
- ALL / MANUAL / IMPORTED selection now has a distinct selected-state colour per filter.
- Deck root rows use the same pastel family rather than the previous green/blue/brown fills.
- Study Menu options and More Tools options use individual pastel cards while retaining their existing click actions.
- Import/pending/error dialog messages use theme-aware rich text: the first status line is emphasized and all message text explicitly follows the active ThemeManager primary text colour.
- Standard Flashcard confirmation/error dialogs also receive theme-aware title/message/button text styling.

## Identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.30`
- versionCode: `128`
- compileSdk: `37`
- targetSdk: `36`
- zstd dependency: `com.github.luben:zstd-jni:1.5.7-16@aar`

## Static/source audit
- XML parse + duplicate IDs within individual layout files: PASS
- Existing findViewById/XML type audit rules retained: PASS
- No Thread.sleep/runBlocking/GlobalScope/killProcess: PASS
- No unsafe `PRAGMA foreign_keys=ON` execSQL path: PASS
- Native adaptive autosize remains disabled: PASS
- No broad `catch(Throwable)`: PASS
- Persistent CI signing configuration retained: PASS
- Flashcard pastel palette checks: PASS
- Theme-aware rich import text checks: PASS
- Existing package ID and monotonically increasing versionCode: PASS
- Provenance certificate Ed25519 verification: PASS

## Runtime-risk audit
- No new startup work introduced.
- No database schema or importer logic changed.
- No changes to SRS scheduling/enforcement.
- No changes to Ben/Dr. Frankenstein, QBank, performance managers, or Battery Engine ownership.
- New UI colour/state code is local to FlashcardActivity and executes only during Flashcard UI construction/rendering.
- Import cancellation/resume callback flow is unchanged; only message rendering/styling changed.

## Provenance
- canonical SHA-256: `e0d05053e352bdf085115f78588b69fe67da13e940386d2004b99a769581c1dd`
- Ed25519 signature verified against the project provenance public key.

## Compilation
Local Gradle compilation was attempted but cannot be completed in this environment because `services.gradle.org`
cannot be resolved by DNS. GitHub Actions remains the authoritative Android compilation environment.
