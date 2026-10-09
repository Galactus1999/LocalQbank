#!/usr/bin/env python3
from pathlib import Path
import re
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
gradle = P / "app/build.gradle.kts"
profile = K / "BenExamProfile.kt"
dialog = K / "BenQuestionAiContextDialog.kt"
policy_test = P / "app/src/test/java/com/localqbank/library/BenQuestionAiModePromptTest.kt"
home = K / "RovexHomeRevolution.kt"
android_test = P / "app/src/androidTest/java/com/localqbank/library/RovexFlowSurfaceRegressionTest.kt"

for required in (gradle, profile, dialog, home, android_test):
    if not required.is_file():
        raise SystemExit(f"[645] required file missing: {required.relative_to(P)}")

g = gradle.read_text()
if 'versionName = "8.3.644"' not in g or "versionCode = 730" not in g:
    raise SystemExit("[645] wrong baseline; expected v8.3.644 / versionCode 730")
gradle.write_text(g.replace('versionName = "8.3.644"', 'versionName = "8.3.645"', 1).replace("versionCode = 730", "versionCode = 731", 1))

# Replace whole prompt entries, rather than appending generic advice that would make the four
# actions converge on the same shallow answer. Keep provenance rules explicit.
p = profile.read_text()
entries = {
"PYQ_CONTEXT": '''    PYQ_CONTEXT("PYQ", "SOURCE POLICY (mandatory): base factual medical claims only on the latest verifiable editions of standard medical textbooks and official institutional/government/academic-body websites. Do not use unofficial websites, blogs, coaching sites, forums, social media, SEO pages, or unattributed summaries. Prefer supplied local textbook extracts and official sources; cite textbook title/edition/page or official institution/page URL when actually available. Never fabricate citations or claim to have checked a source you did not retrieve. If approved sources are unavailable, say verification is unavailable and separate that limitation from local QBank evidence. PYQ evidence audit for the active exam profile. Use only retrieved questions whose source metadata explicitly establishes previous-year-question provenance; list the exam/year/source only when supplied. For each verified PYQ, identify the exact tested concept, decisive stem clue, reasoning that leads to the keyed answer, the nearest distractor and why it fails, and the high-yield fact worth memorising. Synthesize recurring concepts only as counts in this retrieved local sample, never as national exam frequency or a prediction. If no verified PYQ is present, say so clearly and switch to labelled general exam teaching without fabricating a PYQ, year, source, or trend."),''',
"PYT_CONTEXT": '''    PYT_CONTEXT("TOPIC MAP", "SOURCE POLICY (mandatory): base factual medical claims only on the latest verifiable editions of standard medical textbooks and official institutional/government/academic-body websites. Do not use unofficial websites, blogs, coaching sites, forums, social media, SEO pages, or unattributed summaries. Prefer supplied local textbook extracts and official sources; cite textbook title/edition/page or official institution/page URL when actually available. Never fabricate citations or claim to have checked a source you did not retrieve. If approved sources are unavailable, say verification is unavailable and separate that limitation from local QBank evidence. Build a whole-topic revision map for the active exam profile, not merely a list of related questions. Start with the core definition and mechanism; add classifications, classic clinical or image/lab clues, diagnostic approach, management, complications, and key exceptions when relevant. Then map each retrieved PYQ/PYT/QBank item to the exact subtopic it tests and explain how the same concept is reframed across question styles. Contrast close differentials and look-alike distractors, identify common exam traps, and finish with a compact high-yield checklist plus a short recall framework. Use local evidence to support claims about what was asked; do not invent PYQ provenance, years, or frequencies. Clearly label general textbook teaching separately from retrieved local evidence."),''',
"FUTURE_RELATED": '''    FUTURE_RELATED("FUTURE Qs", "SOURCE POLICY (mandatory): factual explanations and answer keys must be grounded only in the latest verifiable editions of standard medical textbooks and official institutional/government/academic-body websites. Never consult or cite unofficial websites, blogs, coaching sites, forums, social media, SEO pages, or unattributed summaries. Cite only sources actually retrieved; do not invent edition numbers, page numbers, URLs, updates, or references. If approved sources are unavailable, disclose that limitation and label any locally grounded practice generation separately. Create a focused future-practice set from the current question and supplied local evidence for the active exam profile. Generate 5–8 genuinely varied, original exam-style question concepts: clinical vignette, next-best step, investigation/image or lab interpretation, mechanism, complication, and close-differential discrimination when appropriate. For each, provide a concise stem, options when useful, correct answer, reasoning, why the strongest distractor is tempting but wrong, difficulty, and the single learning objective. Prioritise under-tested angles and concept transfer rather than paraphrasing retrieved questions. Label every item as practice speculation, never a prediction, leaked question, confirmed trend, or claim about future exam content. Do not invent current guideline updates; flag time-sensitive facts for verification."),''',
"OTHER_OPTIONS": '''    OTHER_OPTIONS("OPTIONS", "SOURCE POLICY (mandatory): base factual medical claims only on the latest verifiable editions of standard medical textbooks and official institutional/government/academic-body websites. Do not use unofficial websites, blogs, coaching sites, forums, social media, SEO pages, or unattributed summaries. Cite textbook title/edition/page or official institution/page URL only when actually available; never fabricate a citation or claim source verification that did not happen. If approved sources are unavailable, state that verification is unavailable. Perform a complete option-by-option discriminator analysis for the current stem. Preserve the supplied authoritative answer key; if the key, explanation, or stem appears inconsistent, flag the ambiguity instead of silently changing the answer. For every option state: its core mechanism/definition, whether it fits this stem and the decisive clue, why it is wrong here, the clinical or exam scenario in which it would become correct, the closest competing option and the one fact that separates them, and a plausible exam-trap/reframing. Include all options, not only the correct answer. End with a comparison table when useful, a one-line elimination strategy, and 3–5 take-home facts suited to NEET-PG/INI-CET revision."),'''
}
for name, replacement in entries.items():
    pattern = rf"^    {name}\(.*\),$"
    p2, count = re.subn(pattern, replacement.rstrip(), p, count=1, flags=re.M)
    if count != 1:
        raise SystemExit(f"[645] could not replace {name} mode prompt (matches={count})")
    p = p2
profile.write_text(p)

s = dialog.read_text()
old_chip = 'modeRow.addView(c, LinearLayout.LayoutParams(dp(activity, 132), dp(activity, 32)).apply { leftMargin = dp(activity, 2); rightMargin = dp(activity, 2) })'
if old_chip not in s:
    raise SystemExit("[645] mode chip fixed-width layout anchor missing")
s = s.replace(old_chip, 'modeRow.addView(c, LinearLayout.LayoutParams(0, dp(activity, 32), 1f).apply { leftMargin = dp(activity, 2); rightMargin = dp(activity, 2) })', 1)
old_mode_scroll = 'modeScroll.addView(modeRow, android.view.ViewGroup.LayoutParams(-2, dp(activity, 34)))'
if old_mode_scroll not in s:
    raise SystemExit("[645] mode row scroll-width anchor missing")
s = s.replace(old_mode_scroll, 'modeScroll.addView(modeRow, android.view.ViewGroup.LayoutParams(-1, dp(activity, 34)))', 1)

# Compact footer action labels while retaining descriptive accessibility labels and stop-state
# semantics. Weighted children consume the available viewport; no horizontal scrolling is needed.
s = s.replace('fun action(label: String, click: () -> Unit) = TextView(activity).apply {\n            text = label\n            textSize = 9.5f', 'fun action(label: String, click: () -> Unit) = TextView(activity).apply {\n            text = label\n            contentDescription = when (label) { "AI" -> "Run Free AI"; "SEARCH" -> "Search this question"; "SAVE" -> "Save answer to notes"; "CLOSE" -> "Close Ben and AI"; else -> label }\n            textSize = 9f', 1)
s = s.replace('val free = action("RUN FREE AI") {', 'val free = action("AI") {', 1)
s = s.replace('freeButton?.text = "RUN FREE AI"', 'freeButton?.text = "AI"')
s = s.replace('buttonState("RUN FREE AI")', 'buttonState("AI")')
old_actions = '''            actions.addView(v, LinearLayout.LayoutParams(dp(activity, 96), dp(activity, 32)).apply {
                leftMargin = if (i == 0) 0 else dp(activity, 4)
                rightMargin = if (i == 3) 0 else dp(activity, 4)
            })'''
if old_actions not in s:
    raise SystemExit("[645] footer fixed-width action layout anchor missing")
s = s.replace(old_actions, '''            actions.addView(v, LinearLayout.LayoutParams(0, dp(activity, 30), 1f).apply {
                leftMargin = dp(activity, 2)
                rightMargin = dp(activity, 2)
            })''', 1)
old_actions_container = 'actionsScroll.addView(actions, android.view.ViewGroup.LayoutParams(-2, dp(activity, 36)))'
if old_actions_container not in s:
    raise SystemExit("[645] footer scroll content-width anchor missing")
s = s.replace(old_actions_container, 'actionsScroll.addView(actions, android.view.ViewGroup.LayoutParams(-1, dp(activity, 32)))', 1)
s = s.replace('bottomChrome.addView(actionsScroll, LinearLayout.LayoutParams(-1, dp(activity, 38)).apply { topMargin = dp(activity, 2) })',
              'bottomChrome.addView(actionsScroll, LinearLayout.LayoutParams(-1, dp(activity, 34)).apply { topMargin = dp(activity, 1) })', 1)
s = s.replace('root.addView(bottomChrome, LinearLayout.LayoutParams(-1, dp(activity, 80)).apply { topMargin = dp(activity, 2) })',
              'root.addView(bottomChrome, LinearLayout.LayoutParams(-1, dp(activity, 76)).apply { topMargin = dp(activity, 1) })', 1)
dialog.write_text(s)

# The old Home motion test required at least eight synthetic surfaces and Lottie layers while the
# safety rollback intentionally disables heuristic wrappers. Test the current safety contract,
# not the obsolete visual implementation; explicit, hand-authored surfaces remain allowed.
android_test.write_text("""package com.localqbank.library

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexFlowSurfaceRegressionTest {
    @Test
    fun homeLayoutDoesNotContainHeuristicAutoGeneratedMotionWrappers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                    ?: error("Home dashboard root missing")
                val autoWrappers = mutableListOf<String>()
                fun walk(view: View) {
                    val tag = view.tag?.toString().orEmpty()
                    if (tag.startsWith("rovex_motion_surface:auto-") || tag == "rovex_motion_clip:auto") autoWrappers.add(tag)
                    if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
                walk(root)
                check(autoWrappers.isEmpty()) {
                    "Heuristic auto-generated Home wrappers must be disabled; found " + autoWrappers
                }
            }
        }
    }
}
""")

policy_test.parent.mkdir(parents=True, exist_ok=True)
policy_test.write_text("""package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenQuestionAiModePromptTest {
    @Test
    fun fourModesHaveDistinctExamPreparationJobs() {
        val modes = listOf(
            BenQuestionAiMode.PYQ_CONTEXT,
            BenQuestionAiMode.PYT_CONTEXT,
            BenQuestionAiMode.FUTURE_RELATED,
            BenQuestionAiMode.OTHER_OPTIONS
        )
        assertEquals(4, modes.map { it.label }.distinct().size)
        assertEquals(4, modes.map { it.prompt }.distinct().size)
        assertTrue(BenQuestionAiMode.PYQ_CONTEXT.prompt.contains("explicitly establishes previous-year-question provenance"))
        assertTrue(BenQuestionAiMode.PYT_CONTEXT.prompt.contains("whole-topic revision map"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("5–8 genuinely varied"))
        assertTrue(BenQuestionAiMode.OTHER_OPTIONS.prompt.contains("option-by-option discriminator analysis"))
        assertTrue(modes.all { it.prompt.contains("SOURCE POLICY (mandatory)") })
        assertTrue(modes.all { it.prompt.contains("official institutional") })
        assertTrue(modes.all { it.prompt.contains("unofficial websites") })
        assertTrue(modes.all { it.prompt.length >= 400 })
    }
}
""")

# Hard postconditions: prevent accidental regression to horizontal overflow and primitive prompts.
final_profile = profile.read_text()
final_dialog = dialog.read_text()
final_test = policy_test.read_text()
for needle in ("previous-year-question provenance", "whole-topic revision map", "practice speculation", "option-by-option discriminator analysis"):
    if needle not in final_profile:
        raise SystemExit(f"[645] missing prompt requirement: {needle}")
if "LinearLayout.LayoutParams(0, dp(activity, 32), 1f)" not in final_dialog:
    raise SystemExit("[645] mode chips are not weighted to fit")
if "LinearLayout.LayoutParams(0, dp(activity, 30), 1f)" not in final_dialog:
    raise SystemExit("[645] footer actions are not weighted to fit")
if 'actionsScroll.addView(actions, android.view.ViewGroup.LayoutParams(-1, dp(activity, 32)))' not in final_dialog:
    raise SystemExit("[645] footer content still uses oversized scroll width")
if "homeLayoutDoesNotContainHeuristicAutoGeneratedMotionWrappers" not in android_test.read_text():
    raise SystemExit("[645] Home safety regression test contract missing")
if "fourModesHaveDistinctExamPreparationJobs" not in final_test:
    raise SystemExit("[645] prompt regression test missing")
print("[645] applied v8.3.645 / versionCode 731")
print("[645] four distinct exam-preparation prompts and concise weighted mode chips implemented")
print("[645] footer actions compacted to four equal-width buttons without horizontal scrolling")
print("[645] Home regression test aligned with disabled heuristic wrappers; mode prompt tests added")
