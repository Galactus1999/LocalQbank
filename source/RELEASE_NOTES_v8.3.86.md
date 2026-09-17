# Rovex v8.3.86

- Integrated installed EmbeddingGemma 300M and Gemma 3 270M accelerators into normal clinical Ben responses via the grounded pipeline.
- Action-oriented Ben commands remain deterministic.
- Added live neural status visibility in the Ben chat surface and retained Adaptive Engine telemetry.
- Normal clinical Ben requests now invoke the installed neural pipeline when policy/governor permits; action commands remain deterministic.
- Neural model policy is armed by default for new installs and one-time migrated legacy defaults; an explicit user OFF/STOP choice remains authoritative. Hard resource governor and failure circuit remain authoritative.
- Made image + personal note saving explicit in Q&A and route it to My Notes.
- Fixed Today Solved count clipping with responsive single-line sizing.
