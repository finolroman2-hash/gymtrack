package com.example.gymtrack.domain

import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.domain.ProgressionCalculator.ChartPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class ProgramProgressScope(
    val targetVolume: Float,
    val targetAvgRir: Float,
    val weekCount: Int = 1,
    val periodNote: String? = null,
)

object ProgramProgressPeriodCalculator {

    fun resolveMuscleGroupKey(
        selectedGroupKey: String?,
        selectedExerciseMuscleTags: String?,
    ): String? = when {
        selectedGroupKey != null -> selectedGroupKey
        selectedExerciseMuscleTags != null -> ExerciseMuscleTags.primaryGroupKey(selectedExerciseMuscleTags)
        else -> null
    }

    fun computeScope(
        period: ChartPeriod,
        isGeneral: Boolean,
        isComparison: Boolean,
        comparisonSessionDates: List<LocalDate>,
        sessionDate: LocalDate?,
        macro: TrainingMacrocycleEntity,
        timeline: List<CycleTimelineItem>,
        groupKey: String,
        groupsByCycle: Map<Long, Map<String, TrainingCycleGroupEntity>>,
        today: LocalDate = LocalDate.now(),
    ): ProgramProgressScope? {
        if (timeline.isEmpty() || macro.activatedAt <= 0L) return null
        val activationWeekStart = activationWeekStart(macro)
        val (weekCount, startOffset, note) = resolveWeekWindow(
            period = period,
            isGeneral = isGeneral,
            isComparison = isComparison,
            comparisonSessionDates = comparisonSessionDates,
            sessionDate = sessionDate,
            activationWeekStart = activationWeekStart,
            today = today,
        )
        val targets = sumWeeklyTargets(
            timeline = timeline,
            macro = macro,
            groupKey = groupKey,
            groupsByCycle = groupsByCycle,
            weekCount = weekCount,
            startWeekOffset = startOffset,
        ) ?: return null
        return ProgramProgressScope(
            targetVolume = targets.first,
            targetAvgRir = targets.second,
            weekCount = weekCount,
            periodNote = note,
        )
    }

    fun buildDashboardProgress(
        groupKey: String,
        setCount: Int,
        avgRir: Float,
        scope: ProgramProgressScope,
    ): SessionGroupProgress = SessionGroupProgress(
        groupKey = groupKey,
        weeklySetCount = setCount.toFloat(),
        targetWeeklyVolume = scope.targetVolume,
        targetAvgRir = scope.targetAvgRir,
        actualAvgRir = avgRir,
    )

    private data class WeekWindow(
        val weekCount: Int,
        val startOffset: Int,
        val note: String?,
    )

    private fun resolveWeekWindow(
        period: ChartPeriod,
        isGeneral: Boolean,
        isComparison: Boolean,
        comparisonSessionDates: List<LocalDate>,
        sessionDate: LocalDate?,
        activationWeekStart: LocalDate,
        today: LocalDate,
    ): WeekWindow {
        if (isComparison && comparisonSessionDates.isNotEmpty()) {
            val sorted = comparisonSessionDates.sorted()
            val fromWeek = sorted.first().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val toWeek = sorted.last().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = calendarWeeksInclusive(fromWeek, toWeek)
            return WeekWindow(
                weekCount = weeks,
                startOffset = weekOffsetFromActivation(activationWeekStart, fromWeek),
                note = if (weeks > 1) {
                    "Comparación: $weeks semanas según ciclos del macrociclo."
                } else {
                    "Comparación: una semana según el ciclo vigente."
                },
            )
        }
        if (!isGeneral && sessionDate != null) {
            val weekStart = sessionDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return WeekWindow(
                weekCount = 1,
                startOffset = weekOffsetFromActivation(activationWeekStart, weekStart),
                note = null,
            )
        }
        if (!isGeneral) {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return WeekWindow(1, weekOffsetFromActivation(activationWeekStart, weekStart), null)
        }
        return when (period) {
            ChartPeriod.DAILY -> WeekWindow(
                weekCount = 1,
                startOffset = weekOffsetFromActivation(
                    activationWeekStart,
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                ),
                note = "Referencia semanal del ciclo vigente. El volumen diario lo defines tú.",
            )
            ChartPeriod.WEEKLY -> {
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                WeekWindow(1, weekOffsetFromActivation(activationWeekStart, weekStart), null)
            }
            ChartPeriod.MONTHLY -> {
                val monthStart = today.withDayOfMonth(1)
                val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
                val fromWeek = maxOfLocalDate(
                    activationWeekStart,
                    monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                )
                val toWeek = monthEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = calendarWeeksInclusive(fromWeek, toWeek)
                WeekWindow(
                    weekCount = weeks,
                    startOffset = weekOffsetFromActivation(activationWeekStart, fromWeek),
                    note = "Objetivo del mes: $weeks semana(s) sumando cada micro/mesociclo.",
                )
            }
            ChartPeriod.ANNUAL -> {
                val yearStart = LocalDate.of(today.year, 1, 1)
                val yearEnd = LocalDate.of(today.year, 12, 31)
                val fromWeek = maxOfLocalDate(
                    activationWeekStart,
                    yearStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                )
                val toWeek = yearEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = calendarWeeksInclusive(fromWeek, toWeek)
                WeekWindow(
                    weekCount = weeks,
                    startOffset = weekOffsetFromActivation(activationWeekStart, fromWeek),
                    note = "Objetivo de ${today.year}: $weeks semana(s) sumando cada micro/mesociclo.",
                )
            }
        }
    }

    private fun sumWeeklyTargets(
        timeline: List<CycleTimelineItem>,
        macro: TrainingMacrocycleEntity,
        groupKey: String,
        groupsByCycle: Map<Long, Map<String, TrainingCycleGroupEntity>>,
        weekCount: Int,
        startWeekOffset: Int,
    ): Pair<Float, Float>? {
        var totalVolume = 0f
        var rirWeightedSum = 0f
        var configuredWeeks = 0
        repeat(weekCount) { index ->
            val macroWeek = TrainingCycleCalculator.macroWeekAtOffset(
                timeline,
                macro,
                startWeekOffset + index,
            )
            val cycle = TrainingCycleCalculator.cycleForMacroWeek(timeline, macroWeek) ?: return@repeat
            val group = groupsByCycle[cycle.entity.id]?.get(groupKey) ?: return@repeat
            if (group.weeklyVolume <= 0f) return@repeat
            configuredWeeks++
            totalVolume += group.weeklyVolume
            rirWeightedSum += group.avgRir * group.weeklyVolume
        }
        if (configuredWeeks == 0 || totalVolume <= 0f) return null
        return totalVolume to (rirWeightedSum / totalVolume)
    }

    fun groupsByCycleId(
        groups: List<TrainingCycleGroupEntity>,
    ): Map<Long, Map<String, TrainingCycleGroupEntity>> =
        groups.groupBy { it.cycleId }.mapValues { (_, list) -> list.associateBy { it.muscleGroupKey } }

    private fun activationWeekStart(macro: TrainingMacrocycleEntity): LocalDate {
        val activated = Instant.ofEpochMilli(macro.activatedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return activated.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun weekOffsetFromActivation(activationWeekStart: LocalDate, weekStart: LocalDate): Int {
        val anchor = activationWeekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val target = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(anchor, target).toInt().coerceAtLeast(0)
    }

    private fun calendarWeeksInclusive(start: LocalDate, end: LocalDate): Int {
        if (end.isBefore(start)) return 1
        val ws = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val we = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (ChronoUnit.WEEKS.between(ws, we) + 1).toInt().coerceAtLeast(1)
    }

    private fun maxOfLocalDate(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b
}
