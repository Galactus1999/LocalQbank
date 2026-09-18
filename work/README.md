# Rovex v8.3.33

Android application: `com.localqbank.library`

This source is based on the v8.3.31 stability baseline and contains the v8.3.32 theme, flashcard-control, QBank visibility, Notes routing, header-bird, splash-wordmark, and CI persistent-signing enforcement corrections.

Build identity: versionCode 131 / versionName 8.3.33.

This follow-up hardens Notes attachment integrity: clearing note text no longer hides an attached image, and deleting a note removes both its database attachment links and private image files.

See `RELEASE_AUDIT_v8.3.32.md` for the release audit. GitHub Actions is the authoritative compiler because local Gradle distribution download is unavailable in the current environment.
