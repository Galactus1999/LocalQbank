package com.localqbank.library

import android.content.Context

enum class BenExamProfile(val label: String, val promptHint: String) {
    NEET_PG("NEET-PG", "Indian postgraduate medical entrance exam; emphasize high-yield concepts, common distractors and NEET-PG-style recall/application."),
    INI_CET("INI-CET", "INI-CET; emphasize integrated multi-system reasoning, image/PYQ interpretation and close distractors."),
    FMGE("FMGE", "FMGE; emphasize broad clinical coverage, core facts, practical clinical reasoning and common distractors."),
    GENERAL("General Medical", "General medical learning; explain clearly without assuming a specific entrance-exam pattern.")
}

private const val BEN_EXAM_PROFILE_PREFS = "ben_exam_profile"
private const val BEN_EXAM_PROFILE_KEY = "profile"

fun currentBenExamProfile(context: Context): BenExamProfile = runCatching {
    val raw=context.applicationContext.getSharedPreferences(BEN_EXAM_PROFILE_PREFS,Context.MODE_PRIVATE)
        .getString(BEN_EXAM_PROFILE_KEY,BenExamProfile.NEET_PG.name) ?: BenExamProfile.NEET_PG.name
    BenExamProfile.valueOf(raw)
}.getOrDefault(BenExamProfile.NEET_PG)

fun setBenExamProfile(context: Context, profile: BenExamProfile) {
    context.applicationContext.getSharedPreferences(BEN_EXAM_PROFILE_PREFS,Context.MODE_PRIVATE)
        .edit().putString(BEN_EXAM_PROFILE_KEY,profile.name).apply()
}

fun benExamPromptContext(context: Context): String {
    val profile=currentBenExamProfile(context)
    return "EXAM PROFILE: ${profile.label}\n${profile.promptHint}"
}

enum class BenQuestionAiMode(val label: String, val prompt: String) {
    PYQ_CONTEXT("PYQ CONTEXT", "Use local evidence to identify related questions with explicit PYQ provenance. Explain the relevant PYQ pattern, but never call a question PYQ unless its local source/provenance establishes that fact."),
    PYT_CONTEXT("PYT CONTEXT", "Use local evidence to map the previous-year/topic/test pattern around this question. Explain recurring concepts and distinctions without inventing exam provenance."),
    FUTURE_RELATED("FUTURE RELATED", "Using only the supplied local evidence and exam profile, generate a bounded set of plausible future related question themes or stems. Clearly label these as possibilities, not predictions or leaked exam content."),
    OTHER_OPTIONS("OTHER OPTIONS", "Analyze every non-keyed option. Explain the discriminating clue, why each distractor is wrong, and when a distractor could be correct in a different clinical context. Do not change the authoritative keyed answer.")
}

