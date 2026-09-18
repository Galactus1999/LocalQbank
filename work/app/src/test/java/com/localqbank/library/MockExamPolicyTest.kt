package com.localqbank.library

import com.localqbank.library.exam.MockExamBlueprint
import com.localqbank.library.exam.MockExamPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockExamPolicyTest {
    @Test fun scoreIsBounded() {
        assertEquals(50, MockExamPolicy.scorePercent(1, 2))
        assertEquals(0, MockExamPolicy.scorePercent(0, 0))
        assertEquals(100, MockExamPolicy.scorePercent(8, 4))
    }

    @Test fun blueprintRejectsInvalidSections() {
        val valid = MockExamBlueprint("x", "Mock", 120, longArrayOf(1, 2, 3), listOf(MockExamBlueprint.Section("s", "Section", 0, 3)))
        val invalid = valid.copy(sections = listOf(MockExamBlueprint.Section("s", "Section", 2, 5)))
        assertTrue(MockExamPolicy.validBlueprint(valid))
        assertFalse(MockExamPolicy.validBlueprint(invalid))
    }
}
