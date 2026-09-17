# Rovex v8.3.87 Release Audit

## Scope
- Base: v8.3.86 source.
- Version: 8.3.87 / versionCode 185.
- Package: `com.localqbank.library`.

## Changes
- Foreground interactive Ben model execution is no longer blocked merely by Android power-save mode when RAM and thermal gates pass.
- Added direct Ben Model Lab with Gemma 3 270M direct chat and EmbeddingGemma 300M semantic comparison.
- Added Activity-owned persistent Gemma Engine/Conversation for the Model Lab; it is initialized off the UI thread and explicitly closed on Activity destruction.
- Redesigned Dr. Frankenstein as a chat-first screen with substantially more answer space and a compact live neural status.
- Added Model Lab access from the Frankenstein header and Adaptive Engine.
- Made missing model artifacts explicit in telemetry instead of silently appearing idle.
- Made the image+note action high-contrast on dark themes.
- User image+note attachments are stored in a dedicated Notes image directory and are no longer counted/listed as Knowledge Vault images.
- Reduced Today's Solved count font further for very large values.

## Static checks
- XML parse: PASS (26 XML files).
- Duplicate IDs: PASS; checked only within each individual layout file.
- `runBlocking`: none.
- `Thread.sleep`: none.
- `GlobalScope`: none.
- Broad `catch(Throwable)`: none.
- Bundled model artifacts (`.tflite`, `.litertlm`, `.task`): none.
- Active build/CI version checks: 185 / 8.3.87.
- ZIP integrity: PASS.

## Android compilation
Local Gradle compilation remains UNVERIFIED because the environment cannot resolve `services.gradle.org` for Gradle 9.3.1. CI remains the authoritative compilation/test gate.

## Research basis
- Google LiteRT-LM Android documentation recommends background initialization because `Engine.initialize()` can take significant time and documents asynchronous message streaming.
- Current LiteRT-LM reports show Gemma 3 270M CPU inference can work while a recent Android GPU report produced endless `<pad>` output; Rovex therefore remains CPU-first.
- Google MediaPipe documentation supports EmbeddingGemma 300M through Text Embedder and documents task-specific formatting and CPU benchmarks.
