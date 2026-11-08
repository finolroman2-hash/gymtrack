package com.example.gymtrack.nutrition

import android.content.Context
import com.example.gymtrack.data.local.dao.OffCatalogDao
import com.example.gymtrack.data.local.entities.toCatalogEntity
import com.example.gymtrack.domain.voice.FuzzyTextNormalizer
import java.io.File

object OffUsedFoods {

    private const val STALE_DUMP = "off_products.csv.gz"

    fun deleteStaleDump(context: Context) {
        File(context.cacheDir, STALE_DUMP).delete()
    }

    suspend fun searchLocal(dao: OffCatalogDao, query: String): List<OffFood> {
        val tokens = FuzzyTextNormalizer.tokenize(query).take(4)
        if (tokens.isEmpty()) return emptyList()
        val padded = tokens + List(4 - tokens.size) { "" }
        val rows = dao.searchTokens(padded[0], padded[1], padded[2], padded[3])
        return FoodSearchRanker.rank(query, rows.map { it.toOffFood() })
    }

    suspend fun remember(dao: OffCatalogDao, food: OffFood) {
        if (!canCache(food) || food.isCustom) return
        val code = food.code.ifBlank { food.name }
        val existing = dao.getByCode(code)
        val now = System.currentTimeMillis()
        dao.upsert(
            food.toCatalogEntity().copy(
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedAt = now,
                isCustom = false,
            ),
        )
    }

    suspend fun saveCustom(dao: OffCatalogDao, food: OffFood): OffFood {
        val code = if (food.isCustom && food.code.startsWith("custom-")) {
            food.code
        } else {
            "custom-${java.util.UUID.randomUUID()}"
        }
        val existing = dao.getByCode(code)
        val now = System.currentTimeMillis()
        val saved = food.copy(
            code = code,
            isCustom = true,
            cacheable = true,
            needsDetails = false,
        )
        dao.upsert(
            saved.toCatalogEntity().copy(
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedAt = now,
                isCustom = true,
            ),
        )
        return saved
    }

    suspend fun clear(context: Context, dao: OffCatalogDao) {
        dao.deleteUsed()
        deleteStaleDump(context)
    }

    /** Open Food Facts sí; FatSecret no (no se puede guardar su catálogo). */
    fun canCache(food: OffFood): Boolean =
        food.cacheable && !food.needsDetails && (food.code.isNotBlank() || food.name.isNotBlank())
}
