package com.prosatfinder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogDao {
    @Query(
        "SELECT * FROM satellites WHERE " +
        "(:q = '' OR name LIKE '%' || :q || '%' OR operator LIKE '%' || :q || '%') AND " +
        "((:inactive = 0 AND status != 'INACTIVE') OR (:inactive = 1 AND status = 'INACTIVE')) AND " +
        "(:cont = '' OR (',' || regions || ',') LIKE '%,' || :cont || ',%') AND " +
        "(:country = '' OR (',' || regions || ',') LIKE '%,' || :country || ',%') AND " +
        "(:favOnly = 0 OR id IN (SELECT satId FROM favorites)) " +
        "ORDER BY position, name"
    )
    abstract fun search(q: String, inactive: Int, cont: String, country: String, favOnly: Int): Flow<List<Satellite>>

    @Query("SELECT regions FROM satellites")
    abstract fun allRegions(): Flow<List<String>>

    @Query("SELECT * FROM transponders WHERE satId = :id ORDER BY frequencyMhz")
    abstract fun transponders(id: String): Flow<List<Transponder>>

    @Query("SELECT COUNT(*) FROM satellites") abstract suspend fun count(): Int
    @Query("DELETE FROM satellites") abstract suspend fun clearSatellites()
    @Insert abstract suspend fun insertSatellites(list: List<Satellite>)
    @Insert abstract suspend fun insertTransponders(list: List<Transponder>)

    @Transaction
    open suspend fun replaceAll(s: List<Satellite>, t: List<Transponder>) {
        clearSatellites()
        insertSatellites(s)
        insertTransponders(t)
    }

    @Query("SELECT satId FROM favorites") abstract fun favoriteIds(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun addFavorite(f: Favorite)
    @Query("DELETE FROM favorites WHERE satId = :id") abstract suspend fun removeFavorite(id: String)
}

@Database(
    entities = [Satellite::class, Transponder::class, Favorite::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CatalogDao
    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: android.content.Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "satfinder.db")
                .build().also { inst = it }
        }
    }
}
