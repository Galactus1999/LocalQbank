package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenSpecialistRegistryTest {
    @Test fun registryIsStableAndContainsCoreRoutes() {
        val entries = BenSpecialistRegistry.entries()
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.any { it.id == "management_reasoner" })
        assertTrue(entries.any { it.id == "diagnostic_reasoner" })
        assertTrue(entries.any { it.id == "mechanism_reasoner" })
        assertTrue(entries.any { it.id == "imaging_reasoner" })
    }
}
