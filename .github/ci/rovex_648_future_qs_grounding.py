#!/usr/bin/env python3
"""Tighten Future Qs grounding, exam-trap analysis, and output structure."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
profile = root / "app/src/main/java/com/localqbank/library/BenExamProfile.kt"
test = root / "app/src/test/java/com/localqbank/library/BenQuestionAiModePromptTest.kt"
if not profile.is_file() or not test.is_file():
    raise SystemExit("[648] required Ben prompt or contract test file is missing")

source = profile.read_text()
replacement = r'''    FUTURE_RELATED("FUTURE Qs", "SOURCE POLICY (mandatory): use only the latest verifiable editions of standard medical textbooks and official institutional, government, or academic-body sources for factual claims. Never use unofficial websites, coaching sites, blogs, forums, social media, SEO pages, or unattributed summaries. Cite only sources actually retrieved; never invent editions, pages, URLs, guideline changes, PYQ provenance, or exam trends. If source verification is unavailable, state that clearly and label the output as locally grounded practice, not verified prediction.\n\nRELEVANCE GATE (mandatory): first identify the exact tested concept, decisive clues, and learning objective in the CURRENT question and supplied local evidence. Every generated question must test that same concept or a clearly stated prerequisite, close differential, complication, mechanism, investigation, or next-step decision. Before including an item, state its one-sentence link to the current concept; if the link is weak, unsupported, or merely shares a broad specialty, discard it. Do not drift into unrelated topics. Do not repeat one generic stem or duplicate the same reasoning across items. If the current stem/evidence is missing or unreadable, ask for it or produce a clearly labelled limited result instead of guessing.\n\nEXAMINER-FRAMING ANALYSIS: explain where a question can be framed from: the key stem clue, core mechanism, close differential, investigation/lab/image pattern, next-best step, complication, contraindication, exception, or a change in one clinical variable. Explain how the examiner could trap a candidate (near-neighbour option, familiar-but-wrong association, sequence/timing error, absolute-word trap, confusing mechanism, or clue that changes the answer). Only use trap types that genuinely fit this concept; do not force all categories. Explain the confusing point and the single discriminator that resolves it.\n\nOUTPUT CONTRACT: return a compact, readable result with these sections in this exact order: (1) CONCEPT ANCHOR — topic, decisive clue, and why it matters; (2) WHERE EXAMINERS CAN FRAME IT — 3–5 specific angles linked to the current concept; (3) TRAPS & CONFUSING DIFFERENTIATORS — tempting error, why it attracts candidates, and the decisive separator; (4) FUTURE PRACTICE QUESTIONS — exactly 4 distinct original practice questions, each using the same fields: Q number and tested angle; Stem; Options A–D; Correct answer; Reasoning tied to stem clues; Why the strongest distractor is tempting but wrong; One-line discriminator; Difficulty; Learning objective; Link to current concept; (5) LAST-MINUTE TAKEAWAYS — 3–5 concise facts. Use numbered headings and consistent labels; never dump repeated field labels without their question stems. Keep stems and explanations concise but medically meaningful. Vary question mechanics rather than superficial wording. Include options only when meaningful; if options are supplied, include one best answer and plausible concept-specific distractors.\n\nSAFETY AGAINST FALSE PREDICTION: label every generated item as ORIGINAL PRACTICE, NOT A PREDICTION. Do not claim these questions will appear, that an angle is confirmed high-frequency, or that an exam trend exists without verified evidence. Prefer concept transfer and underexplored angles supported by the supplied question/textbook evidence. Preserve clinical uncertainty and flag time-sensitive management facts for verification."),'''
lines = source.splitlines()
matches = [i for i, line in enumerate(lines) if line.lstrip().startswith('FUTURE_RELATED("FUTURE Qs"')]
if len(matches) != 1:
    raise SystemExit(f"[648] expected one FUTURE_RELATED prompt, found {len(matches)}")
start_line = matches[0]
# Find the next enum entry by its known declaration shape, allowing annotations
# and blank lines between entries. Restrict the search to the enum body so that
# wrapped string continuation lines cannot be mistaken for a new declaration.
end_line = next(
    (i for i in range(start_line + 1, len(lines))
     if re.match(r"^\s{4}[A-Z][A-Z0-9_]*\s*\(", lines[i])),
    None,
)
if end_line is None:
    raise SystemExit("[648] could not locate the next enum entry boundary")
lines = lines[:start_line] + [replacement] + lines[end_line:]
profile.write_text("\n".join(lines) + "\n")

# Validate the generated enum boundary before Gradle sees this file. The replacement must remain
# a comma-terminated enum entry, and OTHER_OPTIONS must survive as the next declaration.
generated = profile.read_text()
future_lines = [line for line in generated.splitlines() if re.match(r"^\\s{4}FUTURE_RELATED\\(", line)]
other_lines = [line for line in generated.splitlines() if re.match(r"^\\s{4}OTHER_OPTIONS\\(", line)]
if len(future_lines) != 1 or not future_lines[0].rstrip().endswith("),"):
    raise SystemExit("[648] generated FUTURE_RELATED entry must be exactly one comma-terminated Kotlin enum entry")
if len(other_lines) != 1:
    raise SystemExit("[648] OTHER_OPTIONS enum entry was lost or duplicated during replacement")
if generated.index(other_lines[0]) < generated.index(future_lines[0]):
    raise SystemExit("[648] enum order changed unexpectedly; OTHER_OPTIONS precedes FUTURE_RELATED")

t = test.read_text()
anchor = 'assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("5–8 genuinely varied"))'
if anchor not in t:
    raise SystemExit("[648] existing Future Qs prompt contract anchor missing")
t = t.replace(anchor, '''assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("RELEVANCE GATE (mandatory)"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("exactly 4 distinct original practice questions"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("WHERE EXAMINERS CAN FRAME IT"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("TRAPS & CONFUSING DIFFERENTIATORS"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("Link to current concept"))
        assertTrue(BenQuestionAiMode.FUTURE_RELATED.prompt.contains("ORIGINAL PRACTICE, NOT A PREDICTION"))''')
test.write_text(t)
print("[648] Future Qs relevance gate, fixed output contract, examiner framing/trap analysis, and prompt regression assertions applied.")
