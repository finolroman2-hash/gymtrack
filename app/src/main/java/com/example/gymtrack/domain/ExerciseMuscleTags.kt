package com.example.gymtrack.domain

/**
 * Etiquetas fijas por grupo muscular (minúsculas en almacenamiento).
 */
object ExerciseMuscleTags {
    val ALL: List<String> = listOf(
        "biceps",
        "triceps",
        "hombro",
        "cuello",
        "core",
        "espalda",
        "pecho",
        "cuadriceps",
        "aductores",
        "femorales",
        "gluteos",
        "pantorrillas",
        "antebrazos",
    )

    private val allowed: Set<String> = ALL.toSet()

    fun toCsv(tags: Collection<String>): String =
        tags.map { it.trim().lowercase() }.filter { it in allowed }.distinct().sorted().joinToString(",")

    fun fromCsv(raw: String?): Set<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in allowed }
            .toSet()

    const val GROUP_UNTAGGED = "sin etiqueta"

    /** Clave de agrupación: primera etiqueta de [ALL] presente; si ninguna, [GROUP_UNTAGGED]. */
    fun primaryGroupKey(csv: String?): String {
        val tags = fromCsv(csv)
        return ALL.firstOrNull { it in tags } ?: GROUP_UNTAGGED
    }

    fun labelForStorage(tag: String): String = tag.trim().lowercase().takeIf { it in allowed } ?: ""
}
