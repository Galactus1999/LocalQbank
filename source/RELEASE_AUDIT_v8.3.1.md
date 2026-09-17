# Rovex v8.3.1 — Release / Runtime Risk Audit

## Scope
Stability hardening of the v8.3.0 flashcard/APKG release. This release is an update to the existing package `com.localqbank.library`.

## Critical fixes
- Modern Anki `.apkg` support: detects `collection.anki21b` and decompresses Zstandard content before opening SQLite.
- Modern normalized Anki schema support: reads `notetypes`, `fields`, `templates`, and `decks` rather than assuming legacy `col.models`/`col.decks` JSON.
- Protobuf decoding for the modern notetype/template/media structures is handled by a bounded local parser for only the fields required by Rovex.
- Modern media manifest and individual media members tolerate Zstandard-compressed and uncompressed entries.
- Legacy `.anki2` / `.anki21` JSON media/model paths remain supported.
- APKG field metadata accepts JSON objects, strings, and scalar values without `getJSONObject()` assumptions.
- Referenced media remains lazy/extracted only when a card actually references it.
- Ren multi-word retrieval is strict: a multi-token query no longer falls back to unrelated cards that only match a generic token in metadata/explanations.
- Colour-spectrum saved colours now synchronise the HSV marker when a saved colour is loaded.
- Inline QBank image WebViews support pinch zoom and tap-to-fullscreen without enabling JavaScript.

## Source/runtime audit
- XML parsing: PASS
- `findViewById<T>()` vs XML widget type: PASS
- Duplicate layout IDs: PASS
- Unsafe `execSQL("PRAGMA ...")`: none
- Global native auto-size path: none
- `Thread.sleep` / `runBlocking` / `GlobalScope`: none
- `killProcess`: none
- Removed automatic wrong-answer -> flashcard API: none
- Historical `onFling` override: none
- Modern APKG invariants: PASS
- ZIP integrity: PASS

## Build verification
Local Gradle compilation could not be completed because the environment cannot resolve `services.gradle.org` to download Gradle 8.11.1. GitHub Actions CI remains the authoritative Kotlin/Android compilation gate.

## Package continuity
- `applicationId`: `com.localqbank.library`
- `versionCode`: 99
- `versionName`: 8.3.1
- Designed to install over the existing Rovex installation without changing the application identity.
