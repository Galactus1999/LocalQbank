# Rovex Web Companion — isolated proof of concept

This directory is a separate, static PWA prototype. It does **not** replace Android Rovex, access its private files, or connect to its database. The sample questions are synthetic fixtures.

## Run locally

Serve this directory over localhost or HTTPS (service workers require a secure context). For example, from the repository root:

```sh
python3 -m http.server 8000
```

Then open `http://localhost:8000/web-companion/`. On iPad, deploy the static directory to a trusted HTTPS origin, open it in Safari, then use Share → Add to Home Screen. Hosting and install steps are documented by Apple: https://support.apple.com/guide/ipad/open-as-web-app-ipad8f1f7a29/27/ipados/27

## Import bundle schema (v1)

```json
{
  "format": "rovex-study-bundle",
  "schemaVersion": 1,
  "exportedAt": "2026-01-01T00:00:00.000Z",
  "questions": [
    {
      "id": "stable-question-id",
      "subject": "Anatomy",
      "category": "PYQ",
      "stem": "Question text",
      "options": ["Option A", "Option B"],
      "answerIndex": 0,
      "explanation": "Explanation",
      "tags": ["example"]
    }
  ],
  "progress": [
    { "questionId": "stable-question-id", "status": "known", "updatedAt": "2026-01-01T00:00:00.000Z" }
  ],
  "notes": [
    { "id": "stable-note-id", "questionId": "stable-question-id", "text": "User note", "updatedAt": "2026-01-01T00:00:00.000Z" }
  ]
}
```

Import validates the schema, caps JSON input at 25 MiB, and merges only records whose stable IDs are not already present. Existing records are never overwritten in this prototype. Export includes locally stored questions, progress and notes. Do not export or share private study data unless intended.

## Deliberate limits

- No Android database adapter or production exporter yet.
- No server, login, remote API, telemetry or cloud synchronization.
- No SQLite/WebAssembly dependency; IndexedDB is sufficient for the initial small POC.
- The service worker caches only the static app shell, never imported study content.
- Browser storage can be cleared or evicted. Export backups before importing important data.
- Not production-ready: no conflict resolution, encrypted export, full-text index, media bundling, or large-dataset performance claim.

## Acceptance checks

1. Open in a modern browser; console has no errors.
2. Load sample questions, search, filter, reveal answers and record a review.
3. Export JSON and re-import into an empty browser profile.
4. Verify a duplicate import skips existing IDs and preserves the original record.
5. Reload online, then reload offline and verify the shell opens.
6. Test current Safari on the target iPad and current Chrome on Android before calling it supported.
