package com.example.gymtrack.domain

import kotlin.math.roundToInt

enum class NutritionSex { MALE, FEMALE }

enum class NutritionActivity(val multiplier: Float, val label: String) {
    SEDENTARY(1.2f, "Sedentario (poco o nada de ejercicio)"),
    LIGHT(1.375f, "Ligero (1–3 días/semana)"),
    MODERATE(1.55f, "Moderado (3–5 días/semana)"),
    HIGH(1.725f, "Alto (6–7 días/semana)"),
    VERY_HIGH(1.9f, "Muy alto (dos sesiones o trabajo físico)"),
}

enum class NutritionGoalType(val label: String, val detail: String) {
    CUT("Adelgazar", "Déficit ~18 %: prioriza proteína para conservar músculo."),
    MAINTAIN("Mantener", "Calorías de mantenimiento (TDEE)."),
    SLOW_BULK("Subir lento", "Superávit ~7 %: el más eficiente para músculo con poca grasa."),
    BULK("Subir", "Superávit ~12 %: más energía, algo más de grasa."),
}

data class NutritionPlan(
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
)

object NutritionCalculator {

    /**
     * Mifflin–St Jeor + factores de actividad ACSM.
     * Proteína ISSN/Helms (más alta en déficit). Grasa mínima ~0,6 g/kg para hormonas.
     * Carbohidratos rellenan el resto. Fibra ~14 g / 1000 kcal.
     */
    fun plan(
        sex: NutritionSex,
        age: Int,
        heightCm: Float,
        weightKg: Float,
        activity: NutritionActivity,
        goal: NutritionGoalType,
        bodyFatPercent: Float? = null,
    ): NutritionPlan {
        val bmr = mifflin(sex, age, heightCm, weightKg)
        val tdee = (bmr * activity.multiplier).roundToInt()
        val calories = when (goal) {
            NutritionGoalType.CUT -> (tdee * 0.82f).roundToInt()
            NutritionGoalType.MAINTAIN -> tdee
            NutritionGoalType.SLOW_BULK -> (tdee * 1.07f).roundToInt()
            NutritionGoalType.BULK -> (tdee * 1.12f).roundToInt()
        }.coerceAtLeast(1200)

        val lbm = bodyFatPercent?.takeIf { it in 3f..60f }?.let { weightKg * (1f - it / 100f) }
        val proteinPerKg = when {
            goal == NutritionGoalType.CUT && lbm != null -> 2.6f
            goal == NutritionGoalType.CUT -> 2.2f
            goal == NutritionGoalType.MAINTAIN -> 1.8f
            else -> 1.8f
        }
        val proteinBase = lbm ?: weightKg
        val protein = (proteinBase * proteinPerKg).coerceAtLeast(weightKg * 1.6f)

        val fatPerKg = when (goal) {
            NutritionGoalType.CUT -> 0.7f
            else -> 0.8f
        }
        var fat = (weightKg * fatPerKg).coerceAtLeast(weightKg * 0.6f)
        val proteinKcal = protein * 4f
        val fatKcal = fat * 9f
        val remain = (calories - proteinKcal - fatKcal).coerceAtLeast(calories * 0.15f)
        if (proteinKcal + fatKcal > calories * 0.85f) {
            fat = ((calories * 0.25f) / 9f).coerceAtLeast(weightKg * 0.6f)
        }
        val carbs = ((calories - protein * 4f - fat * 9f) / 4f).coerceAtLeast(0f)
        val fiber = ((calories / 1000f) * 14f).coerceIn(18f, 45f)

        return NutritionPlan(
            bmr = bmr.roundToInt(),
            tdee = tdee,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
        )
    }

    fun kcalFromMacros(proteinG: Float, carbsG: Float, fatG: Float): Int =
        (proteinG * 4f + carbsG * 4f + fatG * 9f).roundToInt().coerceAtLeast(0)

    fun mifflin(sex: NutritionSex, age: Int, heightCm: Float, weightKg: Float): Float {
        val base = 10f * weightKg + 6.25f * heightCm - 5f * age
        return if (sex == NutritionSex.MALE) base + 5f else base - 161f
    }

    /** FFMI = masa magra (kg) / estatura² (m). */
    fun ffmi(weightKg: Float, heightCm: Float, bodyFatPercent: Float): Float {
        val heightM = heightCm / 100f
        if (heightM <= 0f) return 0f
        val ffm = weightKg * (1f - bodyFatPercent.coerceIn(0f, 70f) / 100f)
        return ffm / (heightM * heightM)
    }

    fun normalizedFfmi(weightKg: Float, heightCm: Float, bodyFatPercent: Float): Float {
        val heightM = heightCm / 100f
        return ffmi(weightKg, heightCm, bodyFatPercent) + 6.1f * (1.8f - heightM)
    }
}

data class MacroTotals(
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
) {
    operator fun plus(other: MacroTotals) = MacroTotals(
        kcal + other.kcal,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
        fiber + other.fiber,
    )
}
