package com.prosatfinder.data

import kotlinx.serialization.Serializable

/** Katalog JSON şeması (schemaVersion = 1). Bkz. README.md */
@Serializable
data class CatalogFile(
    val schemaVersion: Int,
    val dataVersion: String,
    val updatedAt: String,
    val source: String,
    val sourceUrl: String? = null,
    val satellites: List<SatDto>
)

@Serializable
data class SatDto(
    val id: String,
    val name: String,
    val operator: String? = null,
    val position: Double,
    val status: String = "unverified",
    val countries: List<String> = emptyList(),
    val continents: List<String> = emptyList(),
    val checkedDate: String? = null,
    val transponders: List<TpDto> = emptyList()
)

@Serializable
data class TpDto(
    val band: String,
    val freqMhz: Int,
    val pol: String,
    val symbolRate: Int? = null,
    val fec: String? = null,
    val label: String? = null
)

/** Saf Kotlin; birim testlerle denenir. Geçersiz veriyi düzeltmez, reddeder. */
object CatalogValidator {
    private val statuses = setOf("active", "inactive", "unverified")
    private val pols = setOf("H", "V", "L", "R")
    // Yalnızca downlink aralıkları (MHz)
    private val bands = mapOf("C" to 3400..4800, "Ku" to 10700..12750, "Ka" to 17300..21200)
    private val fecRe = Regex("""\d{1,2}/\d{1,2}""")
    private val dateRe = Regex("""\d{4}-\d{2}-\d{2}""")

    fun validate(f: CatalogFile): List<String> {
        val e = mutableListOf<String>()
        if (f.schemaVersion != 1) e += "Desteklenmeyen schemaVersion: ${f.schemaVersion}"
        if (!dateRe.matches(f.updatedAt)) e += "updatedAt YYYY-MM-DD olmalı"
        if (f.source.isBlank()) e += "source boş olamaz"
        val seen = HashSet<String>()
        for (s in f.satellites) {
            if (s.id.isBlank() || !seen.add(s.id)) e += "Boş veya yinelenen id: '${s.id}'"
            if (s.name.isBlank()) e += "${s.id}: name boş"
            if (s.position !in -180.0..180.0) e += "${s.id}: position aralık dışı (${s.position})"
            if (s.status !in statuses) e += "${s.id}: geçersiz status '${s.status}'"
            if (s.status == "active" && (s.checkedDate == null || !dateRe.matches(s.checkedDate)))
                e += "${s.id}: doğrulama tarihi (checkedDate) olmadan 'active' işaretlenemez"
            for (t in s.transponders) {
                val range = bands[t.band]
                if (range == null) e += "${s.id}: geçersiz band '${t.band}'"
                else if (t.freqMhz !in range) e += "${s.id}: ${t.band} bandı için frekans aralık dışı (${t.freqMhz})"
                if (t.pol !in pols) e += "${s.id}: geçersiz polarizasyon '${t.pol}'"
                if (t.symbolRate != null && t.symbolRate !in 500..60000) e += "${s.id}: symbolRate aralık dışı (${t.symbolRate})"
                if (t.fec != null && !fecRe.matches(t.fec)) e += "${s.id}: geçersiz FEC '${t.fec}'"
            }
        }
        return e
    }
}
