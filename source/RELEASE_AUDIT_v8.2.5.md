# Rovex 8.2.5 — Image Pipeline / Stability Audit

## Image reliability corrections
- Persist SAF read permission for imported HTML document URIs when the provider supports persistable grants.
- Do not use `URI.resolve()` on `content://` document URIs for relative media paths; this can create syntactically valid but unreadable child URIs.
- Preserve content-document-relative images as an internal `rovex-rel://image` reference carrying the original document base and relative path.
- Native bitmap decoding intentionally skips `rovex-rel://image`; the controlled WebView fallback resolves the relative path against the original content document.
- Apply the same relation-aware resolution to fullscreen image viewing.
- Preserve existing bounded bitmap decoding for HTTP/HTTPS/content/file/data images to avoid image-induced OOM.

## Historical crash audit
- No `execSQL("PRAGMA ...")` application usage.
- No global native auto-size text configuration.
- No invalid `selectAllOnFocus = ...` assignment.
- No automatic wrong-question flashcard creation path.
- Typed `findViewById` scan found no missing app-resource IDs; `android.R.id.content` references are framework IDs.
- ZIP/source integrity checked.

## Build gate
The local environment cannot obtain Gradle 8.11.1 because `services.gradle.org` is unreachable. CI compilation remains the final release gate.
