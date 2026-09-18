# Rovex v8.3.89 — Ben INI-CET terminology + true visual-question retrieval

## What changed

- Supersedes the unbuildable v8.3.88 source branch. Baseline is the last known source state before the v8.3.88 `LayoutParams` compiler failure.
- Added `BenExamTerminology`, a bounded offline terminology layer for common INI-CET/NEET-PG student shorthand and MCQ/image vocabulary.
- Added exam-language context to the grounded neural prompt and semantic retrieval query.
- Added explicit planner/tool signals for `exam_terminology` and `visual_question_retrieval`.
- Added hard visual QBank retrieval: a result must have at least one actual imported `question_image`; the presence of the words “image”, “slide”, or “pathology” in text is not sufficient.
- Visual retrieval uses subject/concept metadata and text only to rank actual image-bearing questions.
- Increased visual-question collection retrieval to 500 IDs so “all image-related questions” is not artificially capped at the normal 100-question search limit.
- Normal Frankenstein chat continues to invoke the neural pipeline automatically when a valid model artifact is installed and the governor permits it; direct Model Lab remains optional diagnostic access.
- Gemma normal-chat integration now keeps a bounded CPU-first LiteRT-LM Engine/Conversation alive for up to 2 minutes of inactivity, avoiding repeated initialization on every message.
- Removed verbose neural telemetry from the chat answer body; detailed telemetry remains in the live monitor/Adaptive Engine.
- Fixed a confidence-score precedence issue in the cognitive plan calculation.
- Existing Notes image+note storage/UI fixes remain preserved.

## Important model boundary

EmbeddingGemma 300M is a text embedding/retrieval model, not a visual model. Gemma 3 270M is being used here as a text-generation accelerator. Current Google model documentation lists Gemma 3 270M as text-to-text; multimodal image input is available in larger Gemma 3 variants and Gemma 3n. Therefore v8.3.89 can find image-bearing pathology questions, but it does not claim that the 270M model itself can inspect pathology pixels.

## INI-CET terminology strategy

The terminology layer is local prompt/routing knowledge, not neural weight training. It covers exam shorthand such as PYQ, IBQ, SBA, NBS, high-yield, volatile, integrated question, clinical vignette, negative stem, trap, spotter, histopathology slide, gross specimen, radiograph, ECG, fundus, instrument, gold standard, first-line, DOC, sensitivity/specificity, PPV/NPV, likelihood ratios, RR/OR, ARR/RRR, NNT/NNH, recall/conceptual/application questions, and revision/trend vocabulary.

Current AIIMS prospectus material describes the INI-CET paper as 200 questions over 180 minutes, divided into four 50-question parts with 45 minutes per part and -1/3 marking for an incorrect answer. These exam-format facts should be treated as versioned/session-specific data rather than permanent model weights.
