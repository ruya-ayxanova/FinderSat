package com.prosatfinder

import com.prosatfinder.data.CatalogParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CatalogDataTest {
    private val parsed = CatalogParser.parse(File("src/main/assets/catalog_seed.json").readText())

    @Test fun idsAreUnique() {
        val ids = parsed.sats.map { it.id }
        assertEquals("Tekrarlayan id var", ids.size, ids.toSet().size)
    }

    @Test fun positionsInRange() {
        parsed.sats.forEach { s ->
            assertTrue("${s.id} konum hatalı", s.position == null || s.position in -180.0..180.0)
        }
    }

    @Test fun transponderFieldsValid() {
        parsed.trs.forEach { t ->
            assertTrue(t.polarization == null || t.polarization in listOf("H", "V", "L", "R"))
            assertTrue(t.band == null || t.band in listOf("Ku", "C", "Ka"))
            assertTrue(t.frequencyMhz == null || t.frequencyMhz in 1000..40000)
        }
    }
}
