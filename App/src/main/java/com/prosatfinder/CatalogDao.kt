package com.prosatfinder.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM satellites ORDER BY position, name")
    abstract fun satellites(): Flow<List<SatelliteEntity>>

    @Query("SELECT satId FROM favorites")
    abstract fun favorites(): Flow<List<String>>

    @Query("SELECT * FROM transponders WHERE satId = :id ORDER BY band, freqMhz")
    abstract suspend fun transponders(id: String): List<TransponderEntity>

    @Query("SELECT * FROM catalog_meta WHERE id = 0")
    abstract fun meta(): Flow<CatalogMetaEntity?>

    @Query("SELECT COUNT(*) FROM catalog_meta")
    abstract suspend fun metaCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE satId = :id")
    abstract suspend fun removeFavorite(id: String)

    @Query("DELETE FROM satellites")
    abstract suspend fun clearSatellites()

    @Insert abstract suspend fun insertSatellites(list: List<SatelliteEntity>)
    @Insert abstract suspend fun insertTransponders(list: List<TransponderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setMeta(meta: CatalogMetaEntity)

    /** Tek transaction: başarısız olursa eski katalog olduğu gibi kalır. */
    @Transaction
    open suspend fun replaceCatalog(
        sats: List<SatelliteEntity>, tps: List<TransponderEntity>, meta: CatalogMetaEntity
    ) {
        clearSatellites()
        insertSatellites(sats)
        insertTransponders(tps)
        setMeta(meta)
    }
}

@Database(
    entities = [SatelliteEntity::class, TransponderEntity::class, FavoriteEntity::class, CatalogMetaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun dao(): CatalogDao

    companion object {
        @Volatile private var instance: CatalogDatabase? = null
        fun get(context: Context): CatalogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CatalogDatabase::class.java, "catalog.db")
                .build().also { instance = it }
        }
    }
}
