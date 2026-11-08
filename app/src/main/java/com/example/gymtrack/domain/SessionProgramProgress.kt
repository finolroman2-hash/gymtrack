package com.example.gymtrack.domain

import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.SetEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity

data class SessionGroupProgress(
    val groupKey: String,
    /** Series registradas esta semana para el grupo. */
    val weeklySetCount: Float,
    /** Objetivo semanal (series). */
    val targetWeeklyVolume: Float,
    /** RIR medio objetivo del ciclo activo. */
    val targetAvgRir: Float,
    /** RIR medio real esta semana (0 si no hay series). */
    val actualAvgRir: Float,
) {
    val volumeProgress: Float
        get() = if (targetWeeklyVolume <= 0f) 0f else weeklySetCount / targetWeeklyVolume

    val rirProgress: Float
        get() = if (targetAvgRir <= 0f) 0f else actualAvgRir / targetAvgRir
}

object SessionProgramProgressCalculator {

    fun groupsInSession(
        sessionSets: List<SetEntity>,
        exercises: List<ExerciseEntity>,
    ): Set<String> {
        if (sessionSets.isEmpty()) return emptySet()
        val byId = exercises.associateBy { it.id }
        return sessionSets
            .mapNotNull { set -> byId[set.exerciseId] }
            .map { ExerciseMuscleTags.primaryGroupKey(it.muscleTags) }
            .toSet()
    }

    fun weeklyStatsByGroup(
        weeklySets: List<SetEntity>,
        exercises: List<ExerciseEntity>,
    ): Map<String, Pair<Int, Float>> {
        val byId = exercises.associateBy { it.id }
        val counts = mutableMapOf<String, Int>()
        val rirSums = mutableMapOf<String, Int>()
        for (set in weeklySets) {
            val ex = byId[set.exerciseId] ?: continue
            val key = ExerciseMuscleTags.primaryGroupKey(ex.muscleTags)
            counts[key] = (counts[key] ?: 0) + 1
            rirSums[key] = (rirSums[key] ?: 0) + set.rir
        }
        return counts.mapValues { (key, count) ->
            val avgRir = (rirSums[key] ?: 0).toFloat() / count
            count to avgRir
        }
    }

    fun buildProgressRows(
        sessionGroupKeys: Set<String>,
        weeklyStats: Map<String, Pair<Int, Float>>,
        cycleGroups: List<TrainingCycleGroupEntity>,
    ): List<SessionGroupProgress> {
        if (cycleGroups.isEmpty() || sessionGroupKeys.isEmpty()) return emptyList()
        val groupMap = cycleGroups.associateBy { it.muscleGroupKey }
        return sessionGroupKeys
            .intersect(groupMap.keys)
            .sorted()
            .map { key -> rowForGroup(groupMap.getValue(key), weeklyStats) }
    }

    /** Todas las configuraciones del ciclo (p. ej. panel general de Programación). */
    fun buildProgressRowsForAllGroups(
        weeklyStats: Map<String, Pair<Int, Float>>,
        cycleGroups: List<TrainingCycleGroupEntity>,
    ): List<SessionGroupProgress> {
        if (cycleGroups.isEmpty()) return emptyList()
        return cycleGroups
            .sortedBy { it.muscleGroupKey }
            .map { rowForGroup(it, weeklyStats) }
    }

    private fun rowForGroup(
        target: TrainingCycleGroupEntity,
        weeklyStats: Map<String, Pair<Int, Float>>,
    ): SessionGroupProgress {
        val (count, avgRir) = weeklyStats[target.muscleGroupKey] ?: (0 to 0f)
        return SessionGroupProgress(
            groupKey = target.muscleGroupKey,
            weeklySetCount = count.toFloat(),
            targetWeeklyVolume = target.weeklyVolume,
            targetAvgRir = target.avgRir,
            actualAvgRir = avgRir,
        )
    }
}
