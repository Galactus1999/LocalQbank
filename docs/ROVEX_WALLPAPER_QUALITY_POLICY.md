# Rovex bundled wallpaper quality and provenance policy

**Scope:** Phase 2 theme wallpapers in the Android APK.  
**Policy owner:** Rovex visual engineering; `ThemeManager` remains the single theme-selection owner.  
**Decision:** Bundle a small, curated set of externally sourced CC0 wallpapers; do not generate artwork or silently fetch a moving upstream branch during app runtime.

## Research basis

- [Budgie Backgrounds source repository](https://github.com/BuddiesOfBudgie/budgie-backgrounds) requires wallpaper submissions to be at least 3840x2160, JPEG, and CC0/Public Domain. Its README states that the repository's images are manually reviewed and released under CC0-1.0. Rovex pins an exact upstream commit, stores the source URL in the manifest, and keeps the source image unchanged.
- [Android: Handling bitmaps](https://developer.android.com/develop/ui/views/graphics) explains that full-resolution bitmaps can consume substantial memory and that UI-thread decoding can cause jank/ANRs.
- [Android: Loading large bitmaps efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap) recommends reading image bounds first and decoding a sampled bitmap matched to the display component rather than allocating the original image at full resolution.
- [Android: Bitmap memory management](https://developer.android.com/topic/performance/graphics/manage-memory) covers cache sizing and bitmap reuse. Rovex therefore decodes only the selected theme in background work, samples to bounded display dimensions, and uses a size-bounded LRU cache.
- [Poly Haven license](https://polyhaven.com/license) confirms CC0 is suitable for redistribution and commercial use, but the initial implementation uses one curated source family to keep the legal and quality review tractable. New providers must pass the same license/provenance checks before adoption.

## Selection and redistribution rules

1. Bundle six distinct source images, mapped one-to-one to Light, Pastel, Mint, Sunset, Lavender, and AMOLED.
2. Use only assets whose explicit license allows redistribution in the app. Phase 2 wallpapers must be CC0-1.0.
3. Pin the source repository commit in each URL. Never use a moving `main` URL as the provenance record.
4. Keep the original JPEG bytes unchanged. Do not paint over, generate, or repackage the art as if it were Rovex-original work.
5. Record source URL, pinned commit, retrieval date, theme, human-readable label, source dimensions, byte count, SHA-256, license, and focal-point metadata in `RovexVisualAssetManifest.json`.
6. Never claim an asset is visually approved solely because it passes a checksum or license audit. Automated gates prove provenance/integrity and minimum resolution, not artistic quality.

## Build-time quality gates

The asset pipeline must fail closed if any selected wallpaper:
- cannot be downloaded from the pinned source;
- is not a valid JPEG or is suspiciously small;
- is below 3840x2160;
- has missing or duplicate theme mapping;
- has an unpinned source, non-CC0 license, missing retrieval date, inconsistent dimensions/byte count, or changed source bytes;
- does not match the manifest SHA-256.

The catalog audit must validate the actual files in the selected Android source tree, not merely inspect the pipeline's source code. A build failure must not be hidden by falling back to an unrelated asset.

## Runtime and visual-fidelity rules

- ThemeManager remains authoritative. A user's selected wallpaper takes precedence over the bundled default.
- Use a dedicated drawable that preserves aspect ratio and center-crops instead of stretching landscape artwork to portrait phone dimensions.
- Decode only the active theme wallpaper off the UI thread; inspect dimensions before allocation, sample to bounded screen dimensions, preserve full colour for gradients, and keep the bitmap cache size-bounded.
- Loading is optional decoration: if a wallpaper is unavailable, the existing theme atmosphere remains usable and study/navigation are unaffected.
- Avoid a full-screen stack of opaque layers and expensive per-frame image processing. No wallpaper animation may compete with foreground study.
- Theme changes must request the correct theme's image and refresh after asynchronous loading, without creating a second theme manager or scheduling owner.

## Verification required before release

1. Run the asset-catalog audit against the exact generated Android project and inspect each emitted SHA-256.
2. Run Kotlin/JVM and Android compile/test tasks plus the complete Adaptive Android CI workflow; inspect the complete logs.
3. On a real phone, capture Home, QBank, question, flashcard and settings screenshots in all six themes. Check crop/focal placement, gradients/banding, text contrast, image clarity and custom-wallpaper precedence.
4. Measure cold/warm startup, theme-switch latency, memory, frame jank and thermal/battery impact with the wallpaper enabled and disabled.
5. Inspect the release APK and verify all six resources are packaged. Confirm the selected wallpaper is loaded lazily and the source pack does not trigger network access at runtime.
6. Record the tested commit, source archive hash, APK hash, screenshot evidence and CI run URL. Do not call the visual phase complete based on compilation alone.

## Known limitation

The curated upstream set is desktop-oriented (landscape). The app uses an aspect-ratio-preserving center crop, not a stretch, but this necessarily crops a portion of some images on a tall phone. Human screenshot review is a release gate; if the focal composition fails on device, replace that asset with a separately licensed portrait-friendly CC0 source instead of applying destructive crops to the only retained original.
