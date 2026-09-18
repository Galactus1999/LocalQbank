# Rovex v8.2.6 — Image Pipeline / Chrome-style HTML Media Audit

## Root cause addressed
The QBank solver previously used `BitmapFactory` as the primary renderer and only used WebView as a fallback. DAMS exports can carry question/explanation media as browser-native `data:image/*;base64,...` values, while other QBanks use HTTP(S), content/file URIs, srcset, or relative media references. This made the native path unnecessarily different from Chrome's HTML image pipeline.

## Changes
- WebView/Chromium HTML `<img>` rendering is now the primary path for question/explanation/option images.
- `loadDataWithBaseURL()` is used so URL resolution follows browser/WebView base-URL semantics.
- Data URLs receive a stable non-data base URL.
- HTTP/HTTPS legacy QBank media is supported with WebView mixed-content compatibility.
- Content/file access remains enabled; JavaScript remains disabled in the image renderer.
- Existing native bounded BitmapFactory decoder remains available for fallback/fullscreen handling.
- Large-image decoding remains sampled to avoid the historical OOM class of failures.
- Large-file importer now correctly extracts image values when `question_images` / `explanation_images` are JSON arrays instead of silently converting the whole array to a string.
- srcset candidates continue to be extracted.
- No automatic wrong-question -> flashcard creation was reintroduced.

## Source-specific verification
The Library DAMS_Quiz_Club.html source was inspected. It contains question media as `question_images` values using `data:image/jpeg;base64,...`; the first inspected image decoded as a valid JPEG (279x166). The importer now preserves these array entries and the solver renders them through the Chromium/WebView path.

## Previous crash audit
- SQLite PRAGMA remains API-based; no unsafe `execSQL("PRAGMA ...")` path introduced.
- No global native adaptive text autosizing introduced.
- No `selectAllOnFocus =` misuse introduced.
- Manual-only flashcard policy preserved.
- Version: 8.2.6 / versionCode 94.

## Build status
Static/source audit completed. Gradle compilation could not be completed in this environment because Gradle 8.11.1 is not locally cached and `services.gradle.org` is unreachable. CI compilation remains the release gate.
