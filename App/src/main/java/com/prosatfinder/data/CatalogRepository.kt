package com.prosatfinder.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class CatalogMeta(val version: Int, val source: String, val date: String)
data class ParsedCatalog(val meta: CatalogMeta, val sats: List<Satellite>, val trs: List<Transponder>)

object CatalogParser {
    private fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.int(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.dbl(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null

    fun parse(text: String): ParsedCatalog {
        val root = JSONObject(text)
        val m = root.getJSONObject("meta")
        val meta = CatalogMeta(m.getInt("version"), m.getString("source"), m.getString("date"))
        val sats = ArrayList<Satellite>()
        val trs = ArrayList<Transponder>()
        val arr = root.getJSONArray("satellites")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val status = o.str("status") ?: "UNVERIFIED"
            require(status in setOf("VERIFIED_ACTIVE", "UNVERIFIED", "INACTIVE")) { "Geçersiz status: $id" }
            val pos = o.dbl("position")
            require(pos == null || pos in -180.0..180.0) { "Geçersiz konum: $id" }
            val reg = o.optJSONArray("regions")
            sats += Satellite(
                id, o.getString("name"), o.str("operator"), pos, status,
                (0 until (reg?.length() ?: 0)).joinToString(",") { reg!!.getString(it) }
            )
            val ta = o.optJSONArray("transponders")
            for (j in 0 until (ta?.length() ?: 0)) {
                val t = ta!!.getJSONObject(j)
                trs += Transponder(
                    satId = id, frequencyMhz = t.int("freq"), polarization = t.str("pol"),
                    symbolRate = t.int("sr"), fec = t.str("fec"), band = t.str("band")
                )
            }
        }
        return ParsedCatalog(meta, sats, trs)
    }
}

class CatalogRepository(private val ctx: Context, private val db: AppDatabase) {
    private val dir = File(ctx.filesDir, "catalog").apply { mkdirs() }
    private val current = File(dir, "current.json")
    private val backups = File(dir, "backup").apply { mkdirs() }

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (!current.exists())
            current.writeText(ctx.assets.open("catalog_seed.json").bufferedReader().use { it.readText() })
        if (db.dao().count() == 0) {
            val p = CatalogParser.parse(current.readText())
            db.dao().replaceAll(p.sats, p.trs)
        }
    }

    fun meta(): CatalogMeta? = runCatching { CatalogParser.parse(current.readText()).meta }.getOrNull()

    suspend fun install(text: String) = withContext(Dispatchers.IO) {
        val parsed = CatalogParser.parse(text)
        if (current.exists())
            current.copyTo(File(backups, "catalog_${System.currentTimeMillis()}.json"), overwrite = true)
        backups.listFiles()?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        val tmp = File(dir, "new.tmp").apply { writeText(text) }
        tmp.renameTo(current)
        db.dao().replaceAll(parsed.sats, parsed.trs)
    }

    suspend fun update(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://")) { "Yalnızca HTTPS" }
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            val text = c.inputStream.bufferedReader().use { it.readText() }
            install(text)
        }
    }

    fun hasBackup(): Boolean = backups.listFiles()?.isNotEmpty() == true

    suspend fun restoreLatestBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val latest = backups.listFiles()?.maxByOrNull { it.name } ?: error("Yedek yok")
            install(latest.readText())
        }
    }

    suspend fun importCsv(text: String, source: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val version = (meta()?.version ?: 0) + 1
            val json = CsvImporter.toCatalogJson(text, source, java.time.LocalDate.now().toString(), version)
            install(json)
        }
    }
}
