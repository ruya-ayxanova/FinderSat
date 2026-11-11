package com.prosatfinder

import com.prosatfinder.data.*
import org.junit.Assert.*
import org.junit.Test

class CatalogValidatorTest {
    private fun file(vararg s: SatDto) = CatalogFile(1, "t", "2026-11-11", "test", null, s.toList())
    private fun sat(id: String = "a", status: String = "unverified", checked: String? = null, tps: List<TpDto> = emptyList(), pos: Double = 13.0) =
        SatDto(id, "Sat", null, pos, status, emptyList(), emptyList(), checked, tps)

    @Test fun validFilePasses() {
        val f = file(sat(tps = listOf(TpDto("Ku", 11727, "V", 27500, "3/4"))))
        assertTrue(CatalogValidator.validate(f).isEmpty())
    }
    @Test fun activeWithoutCheckDateRejected() = assertFalse(CatalogValidator.validate(file(sat(status = "active"))).isEmpty())
    @Test fun activeWithCheckDateAccepted() = assertTrue(CatalogValidator.validate(file(sat(status = "active", checked = "2026-11-01"))).isEmpty())
    @Test fun duplicateIdRejected() = assertFalse(CatalogValidator.validate(file(sat("a"), sat("a"))).isEmpty())
    @Test fun positionOutOfRangeRejected() = assertFalse(CatalogValidator.validate(file(sat(pos = 200.0))).isEmpty())
    @Test fun badBandFrequencyPolarizationFecRejected() {
        assertFalse(CatalogValidator.validate(file(sat(tps = listOf(TpDto("Ku", 4000, "V"))))).isEmpty())
        assertFalse(CatalogValidator.validate(file(sat(tps = listOf(TpDto("C", 3800, "X"))))).isEmpty())
        assertFalse(CatalogValidator.validate(file(sat(tps = listOf(TpDto("Ka", 19000, "H", fec = "abc"))))).isEmpty())
    }
    @Test fun unknownFieldsStayNull() {
        val t = TpDto("Ku", 11727, "V")
        assertNull(t.symbolRate); assertNull(t.fec)
    }
}
