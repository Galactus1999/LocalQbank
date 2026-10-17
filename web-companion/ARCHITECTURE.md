# Architecture research and decisions

Research checked on 17 October 2026. This document records why the first implementation is an isolated browser POC and why Android production code is unchanged.

## Findings from primary documentation

1. **iPad home-screen web apps:** Apple documents Safari → Share → Add to Home Screen and the Open as Web App option. The web app icon is device-local; it is not an iPadOS-native Android APK.
   - https://support.apple.com/guide/ipad/open-as-web-app-ipad8f1f7a29/27/ipados/27
   - WebKit's Safari 26 notes say iOS/iPadOS 26 lets users choose web-app behavior for Home Screen additions; a manifest remains useful for app metadata and icons.
   - https://webkit.org/blog/17333/webkit-features-in-safari-26-0/

2. **Offline storage is not a backup guarantee:** WebKit documents quotas and origin-level eviction, including eviction under storage pressure or prolonged inactivity. Users must be able to export their data.
   - https://webkit.org/blog/14403/updates-to-storage-policy/

3. **SQLite in the browser is possible but adds complexity:** SQLite documents OPFS persistence in a worker and Safari support, with implementation/version caveats for older Safari. Use only if real SQL/full-text requirements justify it; the initial POC uses IndexedDB.
   - https://www.sqlite.org/wasm/doc/trunk/persistence.md

4. **Android server reliability:** Android restricts foreground-service starts from the background; a local server should be explicitly user-started and should not be designed as an always-on daemon.
   - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

5. **Android 16 local network permission:** Android's documentation describes local-network restrictions and the permission considerations for inbound and outbound LAN traffic when the relevant compatibility behavior is enabled.
   - https://developer.android.com/about/versions/16/behavior-changes-16

6. **Static hosting:** GitHub Pages can host static sites from a public repository on GitHub Free, but it is not a general private database host or an appropriate place for sensitive user study data. Documented published-site limits include a 1 GB site size and a soft 100 GB/month bandwidth limit.
   - https://docs.github.com/en/pages/getting-started-with-github-pages
   - https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits

## Chosen staged design

- POC UI and assets are isolated under `web-companion/`; Android build/source paths are untouched.
- Browser persistence is IndexedDB; the service worker caches only the static shell, not imported study records.
- Portable import format is versioned JSON with stable IDs and separate question/progress/note records.
- Import has a 25 MiB cap in this POC, validates core fields, merges by ID and skips collisions rather than overwriting existing records.
- Export provides a local recovery path. Exported files may contain private notes/progress and should be treated as sensitive.
- No backend, authentication service, analytics, third-party runtime dependency, or network data synchronization is present.
- Local-server implementation is deferred until the portable-data path and actual usage requirements are tested.

## Known limitations before production use

- This is not yet a production exporter from Rovex's SQLite database.
- The prototype is not tested on the user's actual iPad or Android device.
- It has no merge/conflict-resolution semantics for updates to existing IDs; collisions are deliberately skipped.
- IndexedDB quotas vary and browser data can be evicted. Large datasets and images require real-device testing.
- The POC search is in-memory over imported records, not a production-scale FTS implementation.
- Hosting the static shell publicly is compatible with privacy only while private question bundles and user data remain outside the repository and deployment.
