package com.example.gymtrack.domain

import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class CycleKind(val label: String) {
    MICRO("Microciclo"),
    MESO("Mesociclo"),
}

data class CycleTimelineItem(
    val entity: TrainingCycleEntity,
    val kind: CycleKind,
    val startWeek: Int,
    val endWeek: Int,
    val rangeLabel: String,
)

/** Estado del ciclo vigente para avisos en Programación. */
data class ActiveCycleStatus(
    val item: CycleTimelineItem,
    val index: Int,
    val macroWeek: Int,
    val weekInCycle: Int,
    /** Semanas enteras que faltan hasta el cambio de ciclo (0 = esta es la última). */
    val weeksUntilChange: Int,
    /** Días hasta el próximo lunes (avance de semana de programación). */
    val daysUntilNextWeek: Int,
)

object TrainingCycleCalculator {

    const val MIN_WEEKS = 1
    const val MAX_WEEKS = 48

    /** Más de 4 semanas (~1 mes) → mesociclo. */
    private const val MESO_THRESHOLD_WEEKS = 4

    fun classify(weeks: Int): CycleKind =
        if (weeks > MESO_THRESHOLD_WEEKS) CycleKind.MESO else CycleKind.MICRO

    fun formatWeeks(weeks: Int): String =
        if (weeks == 1) "1 semana" else "$weeks semanas"

    fun isValidWeeks(weeks: Int): Boolean = weeks in MIN_WEEKS..MAX_WEEKS

    fun buildTimeline(cycles: List<TrainingCycleEntity>): List<CycleTimelineItem> {
        var cursorWeek = 1
        return cycles.sortedBy { it.sortOrder }.map { cycle ->
            val start = cursorWeek
            val end = cursorWeek + cycle.weeks - 1
            cursorWeek = end + 1
            CycleTimelineItem(
                entity = cycle,
                kind = classify(cycle.weeks),
                startWeek = start,
                endWeek = end,
                rangeLabel = if (start == end) "Semana $start" else "Semanas $start–$end",
            )
        }
    }

    fun macroTotalWeeks(timeline: List<CycleTimelineItem>): Int =
        timeline.sumOf { it.entity.weeks }

    /**
     * Semana del macrociclo en el offset dado desde la semana de activación (0 = semana de activación).
     * Al superar la duración total, el macrociclo se repite.
     */
    fun macroWeekAtOffset(
        timeline: List<CycleTimelineItem>,
        macro: TrainingMacrocycleEntity,
        weekOffsetFromActivation: Int,
    ): Int {
        val total = macroTotalWeeks(timeline).coerceAtLeast(1)
        val startItem = timeline.find { it.entity.id == macro.startCycleId } ?: timeline.first()
        val anchorMacroWeek = startItem.startWeek + macro.startWeekInCycle.coerceAtLeast(1) - 1
        val rawWeek = anchorMacroWeek + weekOffsetFromActivation.coerceAtLeast(0)
        return ((rawWeek - 1) % total) + 1
    }

    fun cycleForMacroWeek(
        timeline: List<CycleTimelineItem>,
        macroWeek: Int,
    ): CycleTimelineItem? = timeline.find { macroWeek in it.startWeek..it.endWeek }

    /** Semanas calendario (lunes) desde la activación hasta [now]. */
    fun weekOffsetFromActivation(
        macro: TrainingMacrocycleEntity,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (macro.activatedAt <= 0L) return 0
        val activated = Instant.ofEpochMilli(macro.activatedAt).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(activated, today).toInt().coerceAtLeast(0)
    }

    /**
     * Ciclo vigente según semanas calendario desde la activación
     * (mismo criterio que estadísticas; el macrociclo se repite al terminar).
     */
    fun resolveCurrentCycle(
        timeline: List<CycleTimelineItem>,
        macro: TrainingMacrocycleEntity,
        now: Long = System.currentTimeMillis(),
    ): CycleTimelineItem? {
        if (timeline.isEmpty()) return null
        if (!macro.isActive || macro.activatedAt <= 0L) {
            return timeline.find { it.entity.id == macro.startCycleId } ?: timeline.first()
        }
        val macroWeek = macroWeekAtOffset(timeline, macro, weekOffsetFromActivation(macro, now))
        return cycleForMacroWeek(timeline, macroWeek)
    }

    fun resolveActiveCycleStatus(
        timeline: List<CycleTimelineItem>,
        macro: TrainingMacrocycleEntity,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ActiveCycleStatus? {
        if (!macro.isActive || macro.activatedAt <= 0L || timeline.isEmpty()) return null
        val macroWeek = macroWeekAtOffset(timeline, macro, weekOffsetFromActivation(macro, now, zone))
        val item = cycleForMacroWeek(timeline, macroWeek) ?: return null
        val index = timeline.indexOfFirst { it.entity.id == item.entity.id }.coerceAtLeast(0)
        val weekInCycle = (macroWeek - item.startWeek + 1).coerceAtLeast(1)
        val weeksUntilChange = (item.endWeek - macroWeek).coerceAtLeast(0)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val daysUntilNextWeek = ChronoUnit.DAYS.between(today, nextMonday).toInt().coerceAtLeast(0)
        return ActiveCycleStatus(
            item = item,
            index = index,
            macroWeek = macroWeek,
            weekInCycle = weekInCycle,
            weeksUntilChange = weeksUntilChange,
            daysUntilNextWeek = daysUntilNextWeek,
        )
    }

    fun formatActiveCycleNotice(status: ActiveCycleStatus): String {
        val name = status.item.entity.label.ifBlank {
            "${status.item.kind.label} #${status.index + 1}"
        }
        val remaining = when {
            status.weeksUntilChange > 1 ->
                "quedan ${status.weeksUntilChange} semanas"
            status.weeksUntilChange == 1 ->
                "queda 1 semana"
            status.daysUntilNextWeek <= 0 ->
                "cambia esta semana"
            status.daysUntilNextWeek == 1 ->
                "cambia mañana"
            else ->
                "cambia en ${status.daysUntilNextWeek} días"
        }
        return "Activo: $name (sem ${status.weekInCycle}/${status.item.entity.weeks}) · $remaining"
    }

    /** @deprecated Usar [resolveCurrentCycle] con [TrainingMacrocycleEntity]. */
    fun resolveCurrentCycle(
        timeline: List<CycleTimelineItem>,
        activatedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): CycleTimelineItem? {
        if (timeline.isEmpty()) return null
        val stub = TrainingMacrocycleEntity(
            userName = "",
            name = "",
            isActive = activatedAt > 0L,
            activatedAt = activatedAt,
            startCycleId = timeline.first().entity.id,
            startWeekInCycle = 1,
            startDayInWeek = 1,
        )
        return resolveCurrentCycle(timeline, stub, now)
    }

    fun formatMuscleGroupKey(key: String): String =
        if (key == ExerciseMuscleTags.GROUP_UNTAGGED) {
            "Sin etiqueta"
        } else {
            key.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
        }

    /** Grupos disponibles para programación (etiquetas + sin etiqueta). */
    fun allProgrammableGroupKeys(): List<String> =
        ExerciseMuscleTags.ALL + ExerciseMuscleTags.GROUP_UNTAGGED
}
