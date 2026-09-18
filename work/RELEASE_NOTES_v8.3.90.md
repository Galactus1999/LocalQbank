# Rovex v8.3.90 — Ben visual-retrieval compile correction

## Purpose
This is a stability/compile-fix release based on the v8.3.89 source. No new AI feature is intentionally introduced.

## Corrections
- Renamed `BenExamTerminology.Context` to `ExamContext` so it cannot shadow Android `Context`.
- Made the terminology alias-map builder explicitly iterate over the class `entries` list, avoiding collision with `Map.entries`.
- Fixed the terminology loader/typing cascade caused by the `Context`/`Entry` collisions.
- Replaced the unresolved `ScrollView.LayoutParams` reference with `android.widget.FrameLayout.LayoutParams`.
- Fixed the visual retrieval concept lambda in `RenCognitiveEngine`.
- Updated CI version assertions to versionCode 188 / versionName 8.3.90.

## Stability boundary
No model weights are bundled. Visual retrieval remains hard-filtered to actual `question_image` ownership; the current Gemma 3 270M accelerator remains text-only.
