package com.localqbank.library

import com.localqbank.library.ai.context.ContextPack
import com.localqbank.library.ai.context.EvidencePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPackContractTest {
    @Test fun evidencePackKeepsAnswerEvidenceSeparate() {
        val pack = EvidencePack(
            questionEvidence = listOf(EvidencePack.Item(-1, "question")),
            answerEvidence = listOf(EvidencePack.Item(1, "answer")),
            distractorEvidence = listOf(EvidencePack.Item(2, "distractor"))
        )
        assertEquals("answer", pack.authoritativeAnswerEvidence().single().text)
        assertEquals(3, pack.bounded().questionEvidence.size + pack.answerEvidence.size + pack.distractorEvidence.size)
    }

    @Test fun contextPackIsBoundedAndCarriesExamProfile() {
        val pack = ContextPack(
            BenExamProfile.NEET_PG, 1L, "q".repeat(7000),
            listOf(ContextPack.OptionContext("A", "x".repeat(2000))),
            null, "B", "e".repeat(4000)
        ).bounded()
        assertEquals(6000, pack.question.length)
        assertEquals(1800, pack.options.single().text.length)
        assertEquals(3500, pack.qbankExplanation?.length)
        assertTrue(pack.toPromptBlock().contains("NEET-PG"))
    }
}
