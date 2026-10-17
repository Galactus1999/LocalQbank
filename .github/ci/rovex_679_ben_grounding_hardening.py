#!/usr/bin/env python3
"""Phase 679: harden contrastive evidence against malformed retrieval metadata."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
planner_path = root / "app/src/main/java/com/localqbank/library/BenContrastiveEvidencePlanner.kt"
evidence_path = root / "app/src/main/java/com/localqbank/library/BenContrastiveEvidence.kt"
pack_path = root / "app/src/main/java/com/localqbank/library/ai/context/EvidencePack.kt"
test_dir = root / "app/src/test/java/com/localqbank/library"
test_path = test_dir / "BenContrastiveEvidencePlannerTest.kt"
notes_path = root / "RELEASE_NOTES_v8.3.677_BenContrastiveGroundingHardening.md"

required = (gradle, planner_path, evidence_path, pack_path)
if not all(path.is_file() for path in required):
    raise SystemExit("[679] FATAL: required Gradle/evidence source files are missing")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.676"' not in g or not re.search(r"\bversionCode\s*=\s*762\b", g):
    raise SystemExit("[679] FATAL: expected Phase 678 baseline v8.3.676 / code 762")

planner = planner_path.read_text(encoding="utf-8")
old_filter = '.filter { it.text.isNotBlank() }'
new_filter = '.filter { it.sourceId > 0L && it.score.isFinite() && it.text.isNotBlank() }'
if planner.count(old_filter) != 1:
    raise SystemExit(f"[679] FATAL: candidate validation anchor expected once, found {planner.count(old_filter)}")
planner = planner.replace(old_filter, new_filter, 1)
planner_path.write_text(planner, encoding="utf-8")

test_dir.mkdir(parents=True, exist_ok=True)
if test_path.exists():
    raise SystemExit("[679] FATAL: grounding test file already exists; refusing to overwrite")
test_path.write_text(r'''package com.localqbank.library

import com.localqbank.library.ai.context.EvidencePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adversarial, deterministic contracts for Ben's retrieval-role boundary. */
class BenContrastiveEvidencePlannerTest {

    @Test
    fun malformedSourceIdsAndNonFiniteScoresNeverEnterEvidence() {
        val result = BenContrastiveEvidencePlanner.plan(
            query = "clinical question",
            candidates = listOf(
                BenContrastiveEvidencePlanner.Candidate(10L, "valid lower", 0.4),
                BenContrastiveEvidencePlanner.Candidate(20L, "valid higher", 0.8),
                BenContrastiveEvidencePlanner.Candidate(0L, "zero source", 100.0),
                BenContrastiveEvidencePlanner.Candidate(-2L, "negative source", 200.0),
                BenContrastiveEvidencePlanner.Candidate(30L, "NaN score", Double.NaN),
                BenContrastiveEvidencePlanner.Candidate(40L, "positive infinity", Double.POSITIVE_INFINITY),
                BenContrastiveEvidencePlanner.Candidate(50L, "negative infinity", Double.NEGATIVE_INFINITY),
            )
        )

        assertEquals(listOf(20L, 10L), result.answers.map { it.sourceId })
        assertTrue(result.all().none { it.sourceId <= 0L })
        assertTrue(result.all().none { !it.score.isFinite() })
    }

    @Test
    fun rankingIsDeterministicAndDuplicateSourceCannotOccupyTwoRoles() {
        val candidates = listOf(
            BenContrastiveEvidencePlanner.Candidate(33L, "tie source 33", 0.9),
            BenContrastiveEvidencePlanner.Candidate(22L, "older duplicate", 0.7),
            BenContrastiveEvidencePlanner.Candidate(11L, "tie source 11", 0.9),
            BenContrastiveEvidencePlanner.Candidate(22L, "best source 22", 0.95),
            BenContrastiveEvidencePlanner.Candidate(44L, "fourth", 0.1),
        )

        val result = BenContrastiveEvidencePlanner.plan(
            query = "query", candidates = candidates, maxAnswer = 2, maxDistractor = 3
        )

        assertEquals(listOf(22L, 11L), result.answers.map { it.sourceId })
        assertEquals(listOf(33L, 44L), result.distractors.map { it.sourceId })
        assertTrue(result.answers.map { it.sourceId }.toSet().intersect(
            result.distractors.map { it.sourceId }.toSet()
        ).isEmpty())
    }

    @Test
    fun totalCharacterBudgetIncludesQuestionAndClipsRetrievedEvidence() {
        val result = BenContrastiveEvidencePlanner.plan(
            query = "QUERY",
            candidates = listOf(
                BenContrastiveEvidencePlanner.Candidate(1L, "1234567890", 1.0),
                BenContrastiveEvidencePlanner.Candidate(2L, "abcdefghij", 0.5),
            ),
            maxAnswer = 2,
            maxDistractor = 2,
            maxTotalChars = 12,
        )

        assertEquals(12, result.totalChars())
        assertEquals("QUERY", result.question.single().text)
        assertEquals("1234567", result.answers.single().text)
        assertTrue(result.totalChars() <= 12)
    }

    @Test
    fun distractorEvidenceCannotBecomeCitationAuthoritative() {
        val planned = BenContrastiveEvidencePlanner.plan(
            query = "question",
            candidates = listOf(
                BenContrastiveEvidencePlanner.Candidate(101L, "supported answer evidence", 0.95),
                BenContrastiveEvidencePlanner.Candidate(202L, "contradictory distractor evidence", 0.2),
            ),
            maxAnswer = 1,
            maxDistractor = 1,
        )
        val pack: EvidencePack = planned.toEvidencePack()

        assertEquals(listOf(101L), pack.authoritativeAnswerEvidence().map { it.sourceId })
        assertEquals(listOf(202L), pack.distractorEvidence.map { it.sourceId })
        assertTrue(pack.authoritativeAnswerEvidence().none { it.sourceId == 202L })
    }

    @Test
    fun zeroBudgetDoesNotPromoteCandidates() {
        val result = BenContrastiveEvidencePlanner.plan(
            query = "question",
            candidates = listOf(BenContrastiveEvidencePlanner.Candidate(1L, "answer", 1.0)),
            maxTotalChars = 0,
        )

        assertTrue(result.answers.isEmpty())
        assertTrue(result.distractors.isEmpty())
        assertEquals(0, result.totalChars())
    }
}
''', encoding="utf-8")

g = g.replace('versionName = "8.3.676"', 'versionName = "8.3.677"', 1).replace("versionCode = 762", "versionCode = 763", 1)
if 'versionName = "8.3.677"' not in g or not re.search(r"\bversionCode\s*=\s*763\b", g):
    raise SystemExit("[679] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")

notes_path.write_text("""# Rovex v8.3.677 — Ben contrastive evidence hardening

- Reject retrieved candidates with non-positive source IDs or non-finite scores before ranking.
- Add deterministic JVM regression coverage for score ordering, duplicate-source exclusion, bounded context, and answer-vs-distractor citation authority.
- Keep the existing deterministic answer verifier authoritative; this phase changes no model/runtime lifecycle or neural backend policy.
- No Android database schema, package ID, signing configuration, or existing study behavior is intentionally changed.
""", encoding="utf-8")

if new_filter not in planner_path.read_text(encoding="utf-8"):
    raise SystemExit("[679] FATAL: candidate validation postcondition failed")
test = test_path.read_text(encoding="utf-8")
for token in (
    "malformedSourceIdsAndNonFiniteScoresNeverEnterEvidence",
    "rankingIsDeterministicAndDuplicateSourceCannotOccupyTwoRoles",
    "totalCharacterBudgetIncludesQuestionAndClipsRetrievedEvidence",
    "distractorEvidenceCannotBecomeCitationAuthoritative",
    "zeroBudgetDoesNotPromoteCandidates",
):
    if token not in test:
        raise SystemExit("[679] FATAL: missing grounding regression contract: " + token)
print("[679] PASS: malformed retrieval metadata is filtered before evidence ranking.")
print("[679] PASS: five deterministic grounding/budget regression tests added.")
print("[679] PASS: version 8.3.677 / code 763.")
