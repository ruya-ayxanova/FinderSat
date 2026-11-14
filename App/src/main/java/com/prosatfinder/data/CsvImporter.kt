package com.prosatfinder.data

import org.json.JSONArray
import org.json.JSONObject

object CsvImporter {
    private val STATUS = setOf("VERIFIED_ACTIVE", "UNVERIFIED", "INACTIVE")
    private val POL = setOf("H", "V", "L", "R")
    private val BAND = setOf("Ku", "C", "Ka")
    private val COLS = listOf(
        "id", "name", "operator", "position", "status", "regions",
        "freq", "pol", "sr", "fec", "band"
    )

    fun toCatalogJson(csv: String, source: String, date: String, version: Int): String {
        val rows = parse(csv.removePrefix("\uFEFF"))
        require(rows.size > 1) { "empty file" }
        val head = rows[0].map { it.trim().lowercase() }
        val ix = COLS.associateWith { head.indexOf(it) }
        val missing = ix.filterValues { it < 0 }.keys
        require(missing.isEmpty()) { "missing columns: $missing" }

        val errors = ArrayList<String>()
        val sats = LinkedHashMap<String, JSONObject>()

        rows.drop(1).forEachIndexed { i, r ->
            if (r.all { it.isBlank() }) return@forEachIndexed
            val line = i + 2
            fun c(n: String) = r.getOrElse(ix.getValue(n)) { "" }.trim()
            fun err(m: String) { errors += "L$line: $m" }
            fun dbl(n: String): Double? {
                val v = c(n).replace(',', '.')
                if (v.isEmpty()) return null
                return v.toDoubleOrNull().also { if (it == null) err(n) }
            }
            fun int(n: String): Int? {
                val v = c(n)
                if (v.isEmpty()) return null
                return v.toIntOrNull().also { if (it == null) err(n) }
            }

            val id = c("id")
            if (id.isEmpty()) { err("id"); return@forEachIndexed }
            val pos = dbl("position")
            if (pos != null && pos !in -180.0..180.0) err("position")
            val status = c("status").ifEmpty { "UNVERIFIED" }
            if (status !in STATUS) err("status")
            val name = c("name")
            if (name.isEmpty()) err("name")

            val posJson: Any = pos ?: JSONObject.NULL
            val operator: Any = c("operator").ifEmpty { null } ?: JSONObject.NULL
            val regions = c("regions").split(Regex("[;|]")).map { it.trim() }.filter { it.isNotEmpty() }

            val sat = sats.getOrPut(id) {
                JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("operator", operator)
                    .put("position", posJson)
                    .put("status", status)
                    .put("regions", JSONArray(regions))
                    .put("transponders", JSONArray())
            }
            if (sat.getString("name") != name || sat.opt("position") != posJson ||
                sat.getString("status") != status
            ) err("$id: inconsistent satellite fields")

            val freq = int("freq")
            if (freq != null) {
                if (freq !in 1000..40000) err("freq")
                val pol = c("pol").ifEmpty { null }
                val band = c("band").ifEmpty { null }
                if (pol != null && pol !in POL) err("pol")
                if (band != null && band !in BAND) err("band")
                val sr: Any = int("sr") ?: JSONObject.NULL
                val fec: Any = c("fec").ifEmpty { null } ?: JSONObject.NULL
                sat.getJSONArray("transponders").put(
                    JSONObject()
                        .put("freq", freq)
                        .put("pol", pol ?: JSONObject.NULL)
                        .put("sr", sr)
                        .put("fec", fec)
                        .put("band", band ?: JSONObject.NULL)
                )
            }
        }
        require(errors.isEmpty()) { errors.take(5).joinToString("; ") }

        return JSONObject()
            .put("meta", JSONObject().put("version", version).put("source", source).put("date", date))
            .put("satellites", JSONArray(sats.values.toList()))
            .toString()
    }

    private fun parse(s: String): List<List<String>> {
        val first = s.lineSequence().firstOrNull() ?: ""
        val d = if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val f = StringBuilder()
        var q = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                q -> if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { f.append('"'); i++ } else q = false
                } else f.append(ch)
                ch == '"' -> q = true
                ch == d -> { row.add(f.toString()); f.setLength(0) }
                ch == '\n' -> { row.add(f.toString()); f.setLength(0); rows.add(row); row = ArrayList() }
                ch == '\r' -> {}
                else -> f.append(ch)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row.add(f.toString()); rows.add(row) }
        return rows
    }
}
