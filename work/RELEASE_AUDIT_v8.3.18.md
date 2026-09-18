# Rovex v8.3.18 Release Audit

## Requested visual/stability changes
- Launcher splash uses true AMOLED black with a sparse star field and retains the cyan/blue/purple flowing particle waves.
- Splash Rovex reveal uses a bundled Lobster Two cursive font and cool white/cyan ink; the previous golden centre glow and golden wordmark are removed.
- Launcher icon is a cursive R on a light sepia-white background; the previous block/golden R is removed.
- Header lightning is a long, sparse red/gold chase effect that can extend toward settings, down into the search region, sideways, or upward at staggered intervals.
- Dr. Frankenstein display uses a slower 3200 ms travelling hand-wave: active letters rise while adjacent letters dip, producing a continuous break-dance-style wave.
- Disabling colour flow now forces all RovexColorFlowTextView instances to use ThemeManager.text(), preventing light-theme XML colours from becoming unreadable on dark/AMOLED themes.

## Compatibility/stability
- package/applicationId remains `com.localqbank.library`.
- versionCode/versionName bumped monotonically to 116 / 8.3.18.
- compileSdk 37 / targetSdk 36 preserved.
- zstd Android AAR preserved.
- Internal Ren class/package/destination identifiers remain unchanged for compatibility.
- No business-logic manager ownership changed.
- New animation views cancel animators on detach and perform no I/O, network, wake locks, or worker threads.
- Per-layout duplicate-ID rule remains in force.
