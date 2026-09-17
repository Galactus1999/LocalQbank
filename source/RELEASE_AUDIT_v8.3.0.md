# Rovex v8.3.0 — Release Audit

## Scope
This is an update over v8.2.9 using the same application ID `com.localqbank.library` and existing local databases/preferences.

## APKG / Anki importer
- Fixed the remaining `String -> JSONObject` assumption in Anki model field metadata.
- Field metadata now accepts normal Anki field objects, string-shaped field entries, and unexpected scalar values without aborting the whole import.
- Media extraction is now lazy: only media referenced by imported card HTML/CSS is extracted. This reduces peak storage pressure for multi-gigabyte packages and avoids eagerly unpacking unused media.
- Added free-space validation before staging the APKG when the content provider reports a reliable source size.
- Large-file copying remains streaming with a 1 MiB buffer; cards remain batch-transactional and resumable.
- External HTTP/HTTPS links are preserved instead of being mistaken for local media.
- Referenced images in front/back fields are rewritten to local media files and therefore remain available on both question and answer sides.
- APKG media references are matched using manifest key, exact filename, URL-decoded filename, and basename.

Anki compatibility basis: packaged decks contain notes, cards, note types/templates, and optionally media; Anki templates support arbitrary HTML including images, links, and tables. citeturn0search0turn0search4

## Ren search precision
- Multi-word clinical queries now require complete content coverage before strict results can outrank expanded terminology matches.
- Exact multi-word phrases receive a strong ranking boost.
- This specifically prevents a query such as `cervical cancer` from surfacing unrelated cancer questions merely because `cancer` appears in explanation or metadata.

## Colour controls
- Replaced the hue-only colour line with an HSV picker: saturation/value field + hue spectrum.
- Saved colours now round-trip accurately instead of being forced to full saturation/value.
- Live reference preview now renders the actual two-colour gradient rather than a single midpoint colour.
- Greeting-panel colour uses the same corrected picker.

## Flashcard reviewer UX
- Tapping anywhere on the card reveals the answer.
- Bookmark and Mark controls moved to the upper-right of the card, stacked vertically.
- Card Information button removed from the reviewer controls.
- Skip is smaller/thinner and positioned above the rating row, aligned over the Easy side.
- Again/Hard/Good/Easy buttons are shorter and use lighter fills.
- Reviewer header made thinner; study information is separated into a clean plain line below the card counter.
- Flashcard question/answer images open in a fullscreen, pinch-zoomable WebView.
- External HTTP/HTTPS links in cards can be opened through the device browser.

## QBank image zoom
- QBank image taps now open a pinch-zoomable fullscreen WebView instead of a fixed-size bitmap viewer.
- This applies to question and explanation images that pass through the existing image pipeline.

## Stability audit
- XML resource parsing: PASS.
- `findViewById<T>()` vs XML widget type audit: PASS.
- No unsafe `execSQL("PRAGMA ...")`: PASS.
- No global native auto-size typography path: PASS.
- No automatic wrong-answer -> flashcard path: PASS.
- No `Thread.sleep`, `runBlocking`, `GlobalScope`, or `killProcess` in app source: PASS.
- APKG field metadata object/string compatibility check: PASS.
- MediaResolver presence and old unsafe `getJSONObject(i).optString("name")` pattern check: PASS.
- Package/application ID unchanged: `com.localqbank.library`.
- Version: 8.3.0, versionCode 98.

## Build verification
Local Gradle compilation is unavailable in this environment because Gradle 8.11.1 cannot resolve `services.gradle.org`. GitHub CI remains the authoritative compilation/runtime-risk gate. The CI workflow has been updated to label this release v8.3.0.
