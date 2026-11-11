package com.prosatfinder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** position: derece, doğu pozitif / batı negatif (13.0 = 13°E, -7.0 = 7°W). */
@Entity(tableName = "satellites")
data class SatelliteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val operatorName: String?,
    val position: Double,
    val status: String,          // active | inactive | unverified
    val countries: String,       // virgülle ayrılmış ISO 3166 kodları
    val continents: String,      // virgülle ayrılmış UN M.49 kodları (150=Avrupa, 142=Asya, 002=Afrika, 021=K.Amerika, 419=Latin Amerika, 009=Okyanusya)
    val checkedDate: String?     // aktifliğin doğrulandığı tarih (YYYY-MM-DD); yoksa doğrulanmamıştır
)

@Entity(
    tableName = "transponders",
    foreignKeys = [ForeignKey(SatelliteEntity::class, ["id"], ["satId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("satId")]
)
data class TransponderEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val satId: String,
    val band: String,            // C | Ku | Ka
    val freqMhz: Int,            // downlink
    val pol: String,             // H | V | L | R
    val symbolRate: Int?,        // ksym/s, bilinmiyorsa null
    val fec: String?,            // örn. "3/4", bilinmiyorsa null
    val label: String?
)

/** Favoriler katalogdan bağımsız tabloda: katalog güncellenince silinmez. */
@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val satId: String)

@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val id: Int = 0,
    val dataVersion: String,
    val updatedAt: String,
    val source: String,
    val sourceUrl: String?,
    val importedAt: Long
)
