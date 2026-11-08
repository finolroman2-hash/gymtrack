package com.example.gymtrack.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.backup.BackupManager
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.domain.CycleTimelineItem
import com.example.gymtrack.domain.SessionGroupProgress
import com.example.gymtrack.domain.SessionProgramProgressCalculator
import com.example.gymtrack.domain.TrainingCycleCalculator
import com.example.gymtrack.domain.WeekBounds
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MacrocycleProgram(
    val macro: TrainingMacrocycleEntity,
    val timeline: List<CycleTimelineItem>,
)

/** Configuración copiable de RIR + volumen por grupo muscular. */
data class GroupVolumeClipboard(
    val avgRir: Float,
    val weeklyVolume: Float,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainingProgramViewModel(
    private val dao: GymDao,
    private val userName: String,
) : ViewModel() {

    private val _groupClipboard = MutableStateFlow<GroupVolumeClipboard?>(null)
    val groupClipboard: StateFlow<GroupVolumeClipboard?> = _groupClipboard.asStateFlow()

    val programs: StateFlow<List<MacrocycleProgram>> =
        dao.observeMacrocycles(userName)
            .flatMapLatest { macros ->
                if (macros.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        macros.map { macro ->
                            dao.observeCyclesForMacrocycle(macro.id).map { cycles ->
                                MacrocycleProgram(
                                    macro = macro,
                                    timeline = TrainingCycleCalculator.buildTimeline(cycles),
                                )
                            }
                        },
                    ) { items -> items.toList() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    val weeklyProgress: StateFlow<List<SessionGroupProgress>> =
        dao.observeActiveMacrocycle(userName).flatMapLatest { macro ->
            if (macro == null || !macro.isActive) {
                flowOf(emptyList())
            } else {
                dao.observeCyclesForMacrocycle(macro.id).flatMapLatest { cycles ->
                    val timeline = TrainingCycleCalculator.buildTimeline(cycles)
                    val current = TrainingCycleCalculator.resolveCurrentCycle(timeline, macro)
                        ?: return@flatMapLatest flowOf(emptyList())
                    val week = WeekBounds.current()
                    combine(
                        dao.observeCycleGroups(current.entity.id),
                        dao.observeSetsForUserInWeek(userName, week.start, week.end),
                        dao.observeExercises(userName),
                    ) { groups, weeklySets, exercises ->
                        val stats = SessionProgramProgressCalculator.weeklyStatsByGroup(
                            weeklySets,
                            exercises,
                        )
                        SessionProgramProgressCalculator.buildProgressRowsForAllGroups(
                            weeklyStats = stats,
                            cycleGroups = groups,
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    fun cycleGroups(cycleId: Long): Flow<List<TrainingCycleGroupEntity>> =
        dao.observeCycleGroups(cycleId)

    suspend fun exportMacrocycleJson(macroId: Long) =
        BackupManager.buildMacrocycleShareJson(dao, userName, macroId)

    suspend fun importSharedMacrocycle(json: org.json.JSONObject) =
        BackupManager.importMacrocycleForUser(dao, userName, json)

    fun addMacrocycle(name: String) {
        viewModelScope.launch {
            dao.insertMacrocycle(
                TrainingMacrocycleEntity(
                    userName = userName,
                    name = name.trim().ifBlank { "Macrociclo" },
                ),
            )
        }
    }

    fun deleteMacrocycle(macro: TrainingMacrocycleEntity) {
        viewModelScope.launch { dao.deleteMacrocycle(macro) }
    }

    fun deactivateMacrocycle() {
        viewModelScope.launch {
            dao.setActiveMacrocycle(userName, null, 0L)
        }
    }

    fun activateMacrocycle(
        macroId: Long,
        startCycleId: Long,
        startWeekInCycle: Int,
        startDayInWeek: Int,
    ) {
        viewModelScope.launch {
            dao.setActiveMacrocycle(
                userName = userName,
                macroId = macroId,
                activatedAt = System.currentTimeMillis(),
                startCycleId = startCycleId,
                startWeekInCycle = startWeekInCycle.coerceAtLeast(1),
                startDayInWeek = startDayInWeek.coerceIn(1, 7),
            )
        }
    }

    fun addCycle(
        macrocycleId: Long,
        weeks: Int,
        avgRir: Float,
        weeklyVolume: Float,
        label: String = "",
        groupKeys: Collection<String> = emptyList(),
    ) {
        if (!TrainingCycleCalculator.isValidWeeks(weeks)) return
        viewModelScope.launch {
            val nextOrder = dao.maxCycleSortOrder(macrocycleId) + 1
            val cycleId = dao.insertTrainingCycle(
                TrainingCycleEntity(
                    macrocycleId = macrocycleId,
                    userName = userName,
                    sortOrder = nextOrder,
                    weeks = weeks,
                    avgRir = avgRir,
                    weeklyVolume = weeklyVolume,
                    label = label.trim(),
                ),
            )
            if (groupKeys.isNotEmpty()) {
                val cycle = TrainingCycleEntity(
                    id = cycleId,
                    macrocycleId = macrocycleId,
                    userName = userName,
                    sortOrder = nextOrder,
                    weeks = weeks,
                    avgRir = avgRir,
                    weeklyVolume = weeklyVolume,
                    label = label.trim(),
                )
                addCycleGroups(cycle, groupKeys, avgRir, weeklyVolume)
            }
        }
    }

    fun updateCycle(cycle: TrainingCycleEntity) {
        viewModelScope.launch { updateCycleReturningDeactivated(cycle) }
    }

    suspend fun updateCycleReturningDeactivated(cycle: TrainingCycleEntity): Boolean {
        val active = dao.observeActiveMacrocycle(userName).first()
        val wasActive = active?.id == cycle.macrocycleId && active.isActive
        dao.updateTrainingCycle(cycle)
        if (wasActive) {
            dao.setActiveMacrocycle(userName, null, 0L)
            return true
        }
        return false
    }

    fun duplicateCycle(cycle: TrainingCycleEntity) {
        viewModelScope.launch {
            val nextOrder = dao.maxCycleSortOrder(cycle.macrocycleId) + 1
            val newLabel = cycle.label.trim().let { base ->
                if (base.isBlank()) "Copia" else "$base (copia)"
            }
            val newId = dao.insertTrainingCycle(
                cycle.copy(
                    id = 0L,
                    sortOrder = nextOrder,
                    label = newLabel,
                ),
            )
            val groups = dao.observeCycleGroups(cycle.id).first()
            if (groups.isNotEmpty()) {
                dao.upsertCycleGroups(
                    groups.map { it.copy(cycleId = newId) },
                )
            }
        }
    }

    fun deleteCycle(cycle: TrainingCycleEntity) {
        viewModelScope.launch {
            val macroId = cycle.macrocycleId
            dao.deleteTrainingCycle(cycle)
            val remaining = dao.observeCyclesForMacrocycle(cycle.macrocycleId).first()
                .filter { it.id != cycle.id }
                .sortedBy { it.sortOrder }
            remaining.forEachIndexed { index, item ->
                if (item.sortOrder != index) {
                    dao.updateTrainingCycle(item.copy(sortOrder = index))
                }
            }
            deactivateIfActiveMacro(macroId)
        }
    }

    fun moveCycleUp(cycle: TrainingCycleEntity) {
        viewModelScope.launch {
            val cycles = dao.observeCyclesForMacrocycle(cycle.macrocycleId).first()
                .sortedBy { it.sortOrder }
            val i = cycles.indexOfFirst { it.id == cycle.id }
            if (i <= 0) return@launch
            val prev = cycles[i - 1]
            dao.updateTrainingCycle(cycle.copy(sortOrder = prev.sortOrder))
            dao.updateTrainingCycle(prev.copy(sortOrder = cycle.sortOrder))
        }
    }

    fun moveCycleDown(cycle: TrainingCycleEntity) {
        viewModelScope.launch {
            val cycles = dao.observeCyclesForMacrocycle(cycle.macrocycleId).first()
                .sortedBy { it.sortOrder }
            val i = cycles.indexOfFirst { it.id == cycle.id }
            if (i < 0 || i >= cycles.lastIndex) return@launch
            val next = cycles[i + 1]
            dao.updateTrainingCycle(cycle.copy(sortOrder = next.sortOrder))
            dao.updateTrainingCycle(next.copy(sortOrder = cycle.sortOrder))
        }
    }

    fun addCycleGroups(
        cycle: TrainingCycleEntity,
        groupKeys: Collection<String>,
        defaultRir: Float = cycle.avgRir,
        defaultVolume: Float = cycle.weeklyVolume,
    ) {
        if (groupKeys.isEmpty()) return
        viewModelScope.launch {
            val existing = dao.observeCycleGroups(cycle.id).first().map { it.muscleGroupKey }.toSet()
            val toInsert = groupKeys
                .filter { it !in existing }
                .map { key ->
                    TrainingCycleGroupEntity(
                        cycleId = cycle.id,
                        muscleGroupKey = key,
                        avgRir = defaultRir,
                        weeklyVolume = defaultVolume,
                    )
                }
            if (toInsert.isNotEmpty()) dao.upsertCycleGroups(toInsert)
            deactivateIfActiveMacro(cycle.macrocycleId)
        }
    }

    fun updateCycleGroup(group: TrainingCycleGroupEntity) {
        viewModelScope.launch {
            dao.upsertCycleGroup(group)
        }
    }

    fun deleteCycleGroup(group: TrainingCycleGroupEntity, macrocycleId: Long) {
        viewModelScope.launch {
            dao.deleteCycleGroup(group)
            deactivateIfActiveMacro(macrocycleId)
        }
    }

    fun copyGroupConfig(group: TrainingCycleGroupEntity) {
        _groupClipboard.value = GroupVolumeClipboard(group.avgRir, group.weeklyVolume)
    }

    fun copyGroupValues(avgRir: Float, weeklyVolume: Float) {
        _groupClipboard.value = GroupVolumeClipboard(avgRir, weeklyVolume)
    }

    fun pasteGroupConfig(group: TrainingCycleGroupEntity, macrocycleId: Long) {
        val clip = _groupClipboard.value ?: return
        viewModelScope.launch {
            dao.upsertCycleGroup(
                group.copy(avgRir = clip.avgRir, weeklyVolume = clip.weeklyVolume),
            )
            deactivateIfActiveMacro(macrocycleId)
        }
    }

    fun clearGroupClipboard() {
        _groupClipboard.value = null
    }

    private suspend fun deactivateIfActiveMacro(macrocycleId: Long) {
        val active = dao.observeActiveMacrocycle(userName).first()
        if (active?.id == macrocycleId && active.isActive) {
            dao.setActiveMacrocycle(userName, null, 0L)
        }
    }
}
