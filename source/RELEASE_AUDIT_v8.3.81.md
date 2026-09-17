# Rovex 8.3.81 Release Audit

## Changes
- LiteRT-LM Android 0.16.1 dependency added as an optional runtime.
- Gemma 3 270M  artifact is user-installed into app-private storage.
- Runtime uses CPU backend only; no GPU/NPU path is enabled yet.
- Adaptive Engine exposes installation/removal and one-shot neural test.

## Safety
- No model is bundled into APK.
- Startup does not initialize LiteRT-LM.
- Resource governor checks memory, thermal, power-save, foreground state and user enablement before execution.
- Prompt and output are bounded.
- Native Engine and Conversation are always closed with .
- Backend failure circuit remains authoritative.

## Verification
- XML parsing: required CI gate.
- findViewById/XML type audit: required CI gate.
- per-layout duplicate ID audit: required CI gate.
- stale version audit: updated to 8.3.81/versionCode 179.
- zstd ARM64 gate retained.
- persistent signing certificate gate retained.
- catch(Throwable) catastrophic-error audit retained.
- Full Android Gradle compilation: UNVERIFIED locally when Gradle distribution/DNS is unavailable; CI is the build gate.
