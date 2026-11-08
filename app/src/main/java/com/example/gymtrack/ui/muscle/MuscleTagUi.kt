package com.example.gymtrack.ui.muscle

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtrack.R

/** Texto e icono para cada etiqueta de [com.example.gymtrack.domain.ExerciseMuscleTags.ALL]. */
object MuscleTagUi {

    sealed interface IconSpec {
        data class Single(@DrawableRes val resId: Int) : IconSpec
        /** Dos sprites alineados izquierda → derecha (p. ej. aductores1 + aductores2). */
        data class Paired(
            @DrawableRes val leftResId: Int,
            @DrawableRes val rightResId: Int,
        ) : IconSpec
    }

    /** Ajustes visuales por grupo (formas irregulares / pares). */
    data class IconLayout(
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val uniformScale: Float = 1f,
        val pairedWidthMultiplier: Float = 1.35f,
        /** Separación entre mitades del par: 1 = sin juntar extra; 0.4 = 40 % del hueco base. */
        val pairedGapFactor: Float = 1f,
        /** Desplazamiento horizontal extra (fracción de [size]); negativo = izquierda. */
        val offsetXFactor: Float = 0f,
    ) {
        fun pairedGap(size: Dp): Dp {
            if (pairedGapFactor >= 1f) return 0.dp
            val closeBy = (1f - pairedGapFactor) * 0.38f
            return (-size.value * closeBy).dp
        }
    }

    fun displayName(tag: String): String = when (tag) {
        "biceps" -> "Bíceps"
        "triceps" -> "Tríceps"
        "hombro" -> "Hombro"
        "cuello" -> "Cuello"
        "core" -> "Core"
        "espalda" -> "Espalda"
        "pecho" -> "Pecho"
        "cuadriceps" -> "Cuádriceps"
        "aductores" -> "Aductores"
        "femorales" -> "Femorales"
        "gluteos" -> "Glúteos"
        "pantorrillas" -> "Pantorrillas"
        "antebrazos" -> "Antebrazos"
        else -> tag.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(java.util.Locale.getDefault()) else c.toString()
        }
    }

    fun layoutFor(tag: String): IconLayout = when (tag) {
        "pecho" -> IconLayout(
            uniformScale = 0.874f, // 0.76 × 1.15
            scaleX = 1.026f,       // 1.14 × 0.9 (−10 % ancho)
            scaleY = 0.983f, // 0.84 × 1.17
            pairedWidthMultiplier = 1.28f,
        )
        "triceps" -> IconLayout(scaleX = 1.32f, scaleY = 1.15f, offsetXFactor = 0.03f)
        "cuadriceps" -> IconLayout(
            uniformScale = 1.15f,
            pairedGapFactor = 0.4f,
            offsetXFactor = -0.04f,
        )
        "aductores" -> IconLayout(
            uniformScale = 1.15f,
            pairedGapFactor = 0.4f,
            offsetXFactor = -0.04f,
        )
        "femorales" -> IconLayout(
            uniformScale = 1.15f,
            pairedGapFactor = 0.4f,
            offsetXFactor = -0.04f,
        )
        "gluteos" -> IconLayout(uniformScale = 1.1f)
        "pantorrillas" -> IconLayout(scaleX = 1.348f, scaleY = 1.15f) // 1.26×1.07 ancho; +15 % alto
        "espalda" -> IconLayout(uniformScale = 1.18f)
        else -> IconLayout()
    }

    private fun res(light: Int, dark: Int, darkTheme: Boolean) = if (darkTheme) dark else light

    fun iconSpec(tag: String, darkTheme: Boolean): IconSpec = when (tag) {
        "biceps" -> IconSpec.Single(res(R.drawable.muscle_biceps, R.drawable.muscle_biceps_night, darkTheme))
        "triceps" -> IconSpec.Single(res(R.drawable.muscle_triceps, R.drawable.muscle_triceps_night, darkTheme))
        "hombro" -> IconSpec.Single(res(R.drawable.muscle_hombro, R.drawable.muscle_hombro_night, darkTheme))
        "cuello" -> IconSpec.Single(res(R.drawable.muscle_cuello, R.drawable.muscle_cuello_night, darkTheme))
        "core" -> IconSpec.Single(res(R.drawable.muscle_core, R.drawable.muscle_core_night, darkTheme))
        "espalda" -> IconSpec.Single(res(R.drawable.muscle_espalda, R.drawable.muscle_espalda_night, darkTheme))
        "pecho" -> IconSpec.Paired(
            res(R.drawable.muscle_pecho_1, R.drawable.muscle_pecho_1_night, darkTheme),
            res(R.drawable.muscle_pecho_2, R.drawable.muscle_pecho_2_night, darkTheme),
        )
        "cuadriceps" -> IconSpec.Paired(
            res(R.drawable.muscle_cuadriceps_1, R.drawable.muscle_cuadriceps_1_night, darkTheme),
            res(R.drawable.muscle_cuadriceps_2, R.drawable.muscle_cuadriceps_2_night, darkTheme),
        )
        "aductores" -> IconSpec.Paired(
            res(R.drawable.muscle_aductores_1, R.drawable.muscle_aductores_1_night, darkTheme),
            res(R.drawable.muscle_aductores_2, R.drawable.muscle_aductores_2_night, darkTheme),
        )
        "femorales" -> IconSpec.Paired(
            res(R.drawable.muscle_femoral_1, R.drawable.muscle_femoral_1_night, darkTheme),
            res(R.drawable.muscle_femoral_2, R.drawable.muscle_femoral_2_night, darkTheme),
        )
        "gluteos" -> IconSpec.Single(
            res(R.drawable.muscle_gluteos, R.drawable.muscle_gluteos_night, darkTheme),
        )
        "pantorrillas" -> IconSpec.Single(
            res(R.drawable.muscle_pantorrillas, R.drawable.muscle_pantorrillas_night, darkTheme),
        )
        "antebrazos" -> IconSpec.Single(
            res(R.drawable.muscle_antebrazos, R.drawable.muscle_antebrazos_night, darkTheme),
        )
        else -> IconSpec.Single(res(R.drawable.muscle_espalda, R.drawable.muscle_espalda_night, darkTheme))
    }

    @DrawableRes
    fun primaryIconRes(tag: String, darkTheme: Boolean): Int = when (val spec = iconSpec(tag, darkTheme)) {
        is IconSpec.Single -> spec.resId
        is IconSpec.Paired -> spec.leftResId
    }

    /** Devuelve todos los drawable ids usados por los iconos de grupo muscular en el tema dado.
     *  Útil para precargarlos en el caché de drawables al arrancar la app. */
    fun allDrawableResIds(darkTheme: Boolean): List<Int> {
        val ids = mutableListOf<Int>()
        val tags = listOf(
            "biceps", "triceps", "hombro", "cuello", "core", "espalda",
            "pecho", "cuadriceps", "aductores", "femorales",
            "gluteos", "pantorrillas", "antebrazos",
        )
        for (t in tags) {
            when (val spec = iconSpec(t, darkTheme)) {
                is IconSpec.Single -> ids += spec.resId
                is IconSpec.Paired -> { ids += spec.leftResId; ids += spec.rightResId }
            }
        }
        return ids
    }

    fun selectedTagsSummary(tags: Collection<String>): String =
        if (tags.isEmpty()) {
            "Sin etiquetas"
        } else {
            tags.sorted().joinToString(", ") { displayName(it) }
        }
}
