package com.prosatfinder.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Katalog verisinin kaynağı filesDir/catalog/catalog.json dosyasıdır; Room onun aranabilir kopyasıdır.
 * Uygulama dilinden tamamen bağımsızdır. İnternet yalnızca update() içinde kullanılır.
 */
class CatalogRepository(private val context: Context, private val dao: CatalogDao) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "catalog")
    private val catalogFile = File(dir, "catalog.json")
    private val backupDir = File(dir, "backups")
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    var updateUrl: String
        get() = prefs.getString("updateUrl", "") ?: ""
        set(v) = prefs.edit().putString("updateUrl", v.trim()).apply()

    /** İlk açılış: assets'teki başlangıç katalogunu kopyalar ve içe aktarır. İnternet gerekmez. */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        dir.mkdirs()
        if (!catalogFile.exists()) {
            context.assets.open("catalog_seed.json").use { i -> catalogFile.outputStream().use { i.copyTo(it) } }
        }
        if (dao.metaCount() == 0) apply(parse(catalogFile.readText()))
    }

    /** Verilen HTTPS adresinden yeni katalog indirir. Doğrulanmadan hiçbir şey değişmez; eski dosya yedeklenir. */
    suspend fun update(urlStr: String) = withContext(Dispatchers.IO) {
        val url = URL(urlStr.trim())
        require(url.protocol == "https") { "Yalnızca https desteklenir" }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        val text = try {
            require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            require(conn.contentLengthLong <= MAX_BYTES) { "Dosya çok büyük" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
        require(text.length <= MAX_BYTES) { "Dosya çok büyük" }

        val parsed = parse(text)        // doğrulama burada; hata varsa fırlatır, hiçbir şey değişmez
        backup()
        try {
            dir.mkdirs()
            val tmp = File(dir, "catalog.json.tmp")
            tmp.writeText(text)
            check(tmp.renameTo(catalogFile) || (catalogFile.delete() && tmp.renameTo(catalogFile))) { "Yazılamadı" }
            apply(parsed)
        } catch (e: Exception) {
            restoreLatestBackup()
            throw e
        }
    }

    private fun parse(text: String): CatalogFile {
        val f = json.decodeFromString<CatalogFile>(text)
        val errors = CatalogValidator.validate(f)
        require(errors.isEmpty()) { errors.take(5).joinToString("; ") }
        return f
    }

    private suspend fun apply(f: CatalogFile) {
        val sats = f.satellites.map {
            SatelliteEntity(it.id, it.name, it.operator, it.position, it.status,
                it.countries.joinToString(","), it.continents.joinToString(","), it.checkedDate)
        }
        val tps = f.satellites.flatMap { s ->
            s.transponders.map { TransponderEntity(satId = s.id, band = it.band, freqMhz = it.freqMhz,
                pol = it.pol, symbolRate = it.symbolRate, fec = it.fec, label = it.label) }
        }
        dao.replaceCatalog(sats, tps,
            CatalogMetaEntity(0, f.dataVersion, f.updatedAt, f.source, f.sourceUrl, System.currentTimeMillis()))
    }

    private fun backup() {
        if (!catalogFile.exists()) return
        backupDir.mkdirs()
        catalogFile.copyTo(File(backupDir, "catalog-${System.currentTimeMillis()}.json"), overwrite = true)
        backupDir.listFiles()?.sortedBy { it.name }?.dropLast(MAX_BACKUPS)?.forEach { it.delete() }
    }

    private fun restoreLatestBackup() {
        backupDir.listFiles()?.maxByOrNull { it.name }?.copyTo(catalogFile, overwrite = true)
    }

    companion object {
        private const val MAX_BYTES = 20L * 1024 * 1024
        private const val MAX_BACKUPS = 5
    }
}
