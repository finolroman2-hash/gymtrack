package com.example.gymtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gymtrack.data.local.entities.OffCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OffCatalogDao {

    @Query("SELECT COUNT(*) FROM off_catalog WHERE isCustom = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM off_catalog WHERE isCustom = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM off_catalog WHERE isCustom = 1 ORDER BY lastUsedAt DESC, nameNormalized ASC")
    fun observeCustom(): Flow<List<OffCatalogEntity>>

    @Query(
        """
        SELECT * FROM off_catalog
        WHERE isCustom = 0
          AND (nameNormalized LIKE '%' || :t0 || '%' OR brandNormalized LIKE '%' || :t0 || '%')
          AND (:t1 = '' OR nameNormalized LIKE '%' || :t1 || '%' OR brandNormalized LIKE '%' || :t1 || '%')
          AND (:t2 = '' OR nameNormalized LIKE '%' || :t2 || '%' OR brandNormalized LIKE '%' || :t2 || '%')
          AND (:t3 = '' OR nameNormalized LIKE '%' || :t3 || '%' OR brandNormalized LIKE '%' || :t3 || '%')
        ORDER BY useCount DESC, lastUsedAt DESC
        LIMIT 40
        """,
    )
    suspend fun searchTokens(t0: String, t1: String, t2: String, t3: String): List<OffCatalogEntity>

    @Query("SELECT * FROM off_catalog WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): OffCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OffCatalogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OffCatalogEntity)

    @Query("DELETE FROM off_catalog WHERE isCustom = 0")
    suspend fun deleteUsed()

    @Query("DELETE FROM off_catalog")
    suspend fun deleteAll()
}
