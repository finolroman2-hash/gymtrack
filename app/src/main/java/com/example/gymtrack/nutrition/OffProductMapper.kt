package com.example.gymtrack.nutrition

import com.example.gymtrack.data.local.entities.OffCatalogEntity
import org.json.JSONObject

internal object OffProductMapper {

    fun fromSearchJson(obj: JSONObject): OffCatalogEntity? {
        val n = obj.optJSONObject("nutriments") ?: return null
        val name = obj.optString("product_name_es").ifBlank { obj.optString("product_name") }
            .ifBlank { return null }
        val kcal100 = n.kcal("energy-kcal_100g")
            ?: n.kcal("energy-kcal")
            ?: n.optDouble("energy_100g", Double.NaN).takeIf { !it.isNaN() }?.div(4.184)?.toFloat()
            ?: return null
        val protein = n.num("proteins_100g") ?: return null
        val carbs = n.num("carbohydrates_100g") ?: return null
        val fat = n.num("fat_100g") ?: return null
        if (kcal100 <= 0f && protein <= 0f && carbs <= 0f && fat <= 0f) return null
        val servingQty = n.num("serving-quantity")
            ?: obj.optDouble("serving_quantity", Double.NaN).toFloatOrNull()
        val servingFromSize = parseGrams(obj.optString("serving_size"))
        val servingGrams = servingQty?.takeIf { it > 0f } ?: servingFromSize
        val hint = listOf(
            obj.optString("quantity"),
            obj.optString("serving_size"),
            obj.opt("categories_tags")?.toString().orEmpty(),
        ).joinToString(" ")
        return OffCatalogEntity.create(
            code = obj.optString("code").ifBlank { name },
            name = name,
            brand = obj.optString("brands"),
            kcal100g = kcal100,
            protein100g = protein,
            carbs100g = carbs,
            fat100g = fat,
            fiber100g = n.num("fiber_100g") ?: 0f,
            kcal100ml = n.kcal("energy-kcal_100ml"),
            servingGrams = servingGrams,
            servingLabel = obj.optString("serving_size").ifBlank { null },
            quantityHint = hint,
        )
    }

    private fun JSONObject.kcal(key: String): Float? =
        num(key)?.takeIf { it >= 0f }

    private fun JSONObject.num(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toFloat()
            else -> optString(key).replace(',', '.').toFloatOrNull()
        }?.takeIf { it >= 0f }
    }

    private fun Double.toFloatOrNull(): Float? =
        if (isNaN() || this < 0.0) null else toFloat()

    internal fun parseGrams(serving: String): Float? {
        if (serving.isBlank()) return null
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|gramo)""", RegexOption.IGNORE_CASE)
            .find(serving) ?: return null
        return match.groupValues[1].replace(',', '.').toFloatOrNull()
    }
}
