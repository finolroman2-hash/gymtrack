package com.example.gymtrack.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gymtrack.domain.voice.FuzzyTextNormalizer
import com.example.gymtrack.nutrition.OffFood

@Entity(
    tableName = "off_catalog",
    indices = [Index("nameNormalized")],
)
data class OffCatalogEntity(
    @PrimaryKey val code: String,
    val name: String,
    val nameNormalized: String,
    val brand: String,
    val brandNormalized: String,
    val kcal100g: Float,
    val protein100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
    val fiber100g: Float,
    val kcal100ml: Float?,
    val servingGrams: Float?,
    val servingLabel: String?,
    val quantityHint: String,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val isCustom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toOffFood() = OffFood(
        code = code,
        name = name,
        brand = brand,
        kcal100g = kcal100g,
        protein100g = protein100g,
        carbs100g = carbs100g,
        fat100g = fat100g,
        fiber100g = fiber100g,
        kcal100ml = kcal100ml,
        servingGrams = servingGrams,
        servingLabel = servingLabel,
        quantityHint = quantityHint,
        isGeneric = brand.isBlank(),
        needsDetails = false,
        cacheable = true,
        isCustom = isCustom,
        servingKcal = servingGrams?.let { g -> if (g > 0f) kcal100g * g / 100f else null },
        servingProtein = servingGrams?.let { g -> if (g > 0f) protein100g * g / 100f else null },
        servingCarbs = servingGrams?.let { g -> if (g > 0f) carbs100g * g / 100f else null },
        servingFat = servingGrams?.let { g -> if (g > 0f) fat100g * g / 100f else null },
        servingFiber = servingGrams?.let { g -> if (g > 0f) fiber100g * g / 100f else null },
    )

    companion object {
        fun create(
            code: String,
            name: String,
            brand: String,
            kcal100g: Float,
            protein100g: Float,
            carbs100g: Float,
            fat100g: Float,
            fiber100g: Float,
            kcal100ml: Float?,
            servingGrams: Float?,
            servingLabel: String?,
            quantityHint: String,
            isCustom: Boolean = false,
        ) = OffCatalogEntity(
            code = code,
            name = name,
            nameNormalized = FuzzyTextNormalizer.normalize(name),
            brand = brand,
            brandNormalized = FuzzyTextNormalizer.normalize(brand),
            kcal100g = kcal100g,
            protein100g = protein100g,
            carbs100g = carbs100g,
            fat100g = fat100g,
            fiber100g = fiber100g,
            kcal100ml = kcal100ml,
            servingGrams = servingGrams,
            servingLabel = servingLabel,
            quantityHint = quantityHint,
            isCustom = isCustom,
        )
    }
}

fun OffFood.toCatalogEntity() = OffCatalogEntity.create(
    code = code.ifBlank { name },
    name = name,
    brand = brand,
    kcal100g = kcal100g,
    protein100g = protein100g,
    carbs100g = carbs100g,
    fat100g = fat100g,
    fiber100g = fiber100g,
    kcal100ml = kcal100ml,
    servingGrams = servingGrams,
    servingLabel = servingLabel,
    quantityHint = quantityHint,
    isCustom = isCustom,
)
