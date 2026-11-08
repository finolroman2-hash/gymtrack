package com.example.gymtrack.nutrition

import com.example.gymtrack.domain.voice.FuzzyStringSimilarity
import com.example.gymtrack.domain.voice.FuzzyTextNormalizer
import java.util.Locale

internal object FoodSearchRanker {

    fun rank(query: String, foods: List<OffFood>): List<OffFood> {
        val qNorm = FuzzyTextNormalizer.normalize(query)
        val qTokens = FuzzyTextNormalizer.tokenize(query)
        if (qNorm.isBlank()) return foods
        return foods
            .map { it to score(qNorm, qTokens, it) }
            .sortedWith(
                compareByDescending<Pair<OffFood, Float>> { it.second }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.brand.length },
            )
            .map { it.first }
    }

    private fun score(qNorm: String, qTokens: List<String>, food: OffFood): Float {
        val name = FuzzyTextNormalizer.normalize(food.name)
        val brand = FuzzyTextNormalizer.normalize(food.brand)
        val full = FuzzyTextNormalizer.normalize(listOf(food.brand, food.name).filter { it.isNotBlank() }.joinToString(" "))
        val nameTokens = FuzzyTextNormalizer.tokenize(food.name)
        if (name.isBlank()) return 0f

        var score = 0f
        if (name == qNorm || full == qNorm) score += 12_000f
        else if (name.startsWith(qNorm)) score += 8_500f
        else if (full.startsWith(qNorm)) score += 7_200f
        else if (name.contains(qNorm)) score += 4_800f
        else if (full.contains(qNorm)) score += 3_200f

        score += FuzzyStringSimilarity.similarity(qNorm, name) * 2_400f
        score += FuzzyStringSimilarity.tokenMatchScore(qTokens, nameTokens) * 3_600f

        var missing = 0
        qTokens.forEach { token ->
            when {
                nameTokens.any { it == token } -> score += 900f
                nameTokens.any { it.startsWith(token) || token.startsWith(it) } -> score += 650f
                name.contains(token) -> score += 400f
                brand.contains(token) -> score += 180f
                else -> {
                    missing++
                    score -= 700f
                }
            }
        }
        if (qTokens.isNotEmpty() && missing == 0) score += 1_500f

        if (food.isGeneric) score += 280f
        if (food.kcal100g > 0f || food.servingKcal != null) score += 120f
        if (brand.isNotBlank() && qTokens.none { brand.contains(it) }) score -= 90f
        score -= name.length * 4f
        return score
    }
}

internal fun parsePer100Description(description: String): MacroPortion? {
    val d = description.lowercase(Locale.ROOT)
    if (!d.contains("100")) return null
    fun grab(label: String): Float? {
        val match = Regex(
            """$label\s*[:=]?\s*(\d+(?:[.,]\d+)?)""",
            RegexOption.IGNORE_CASE,
        ).find(description) ?: return null
        return match.groupValues[1].replace(',', '.').toFloatOrNull()
    }
    val kcal = grab("calories") ?: grab("kcal") ?: return null
    return MacroPortion(
        grams = 100f,
        kcal = kcal,
        protein = grab("protein") ?: 0f,
        carbs = grab("carb") ?: 0f,
        fat = grab("fat") ?: 0f,
        fiber = grab("fiber") ?: grab("fibre") ?: 0f,
    )
}
