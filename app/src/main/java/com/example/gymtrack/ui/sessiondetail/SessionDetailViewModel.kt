package com.example.gymtrack.ui.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.cache.SessionDataCache
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SetEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.data.local.entities.UnitType
import com.example.gymtrack.domain.ExerciseMuscleTags
import com.example.gymtrack.domain.SessionGroupProgress
import com.example.gymtrack.domain.SessionProgramProgressCalculator
import com.example.gymtrack.domain.TrainingCycleCalculator
import com.example.gymtrack.domain.WeekBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(
    private val dao: GymDao,
    val sessionId: Long,
    private val userName: String
) : ViewModel() {

    val session: StateFlow<SessionEntity?> = dao.observeSession(sessionId)
        .onEach { SessionDataCache.putSession(sessionId, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(2000),
            SessionDataCache.sessionFor(sessionId),
        )

    val exercises: StateFlow<List<ExerciseEntity>> = dao.observeExercises(userName)
        .onEach { SessionDataCache.putExercises(userName, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(2000),
            SessionDataCache.exercisesFor(userName),
        )

    val sets: StateFlow<List<SetEntity>> = dao.observeSetsBySession(sessionId)
        .onEach { SessionDataCache.putSets(sessionId, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(2000),
            SessionDataCache.setsFor(sessionId),
        )

    val programGroupProgress: StateFlow<List<SessionGroupProgress>> = combine(
        dao.observeActiveMacrocycle(userName),
        exercises,
        sets,
        session,
    ) { macro, exList, sessionSets, sess ->
        SessionProgressInputs(macro, exList, sessionSets, sess)
    }.flatMapLatest { input ->
        val macro = input.macro
        if (macro == null || !macro.isActive || input.sess == null) {
            return@flatMapLatest flowOf(emptyList())
        }
        val sessionKeys = SessionProgramProgressCalculator.groupsInSession(input.sessionSets, input.exList)
        if (sessionKeys.isEmpty()) {
            return@flatMapLatest flowOf(emptyList())
        }
        val at = input.sess.dateTimestamp
        val week = WeekBounds.forTimestamp(at)
        dao.observeCyclesForMacrocycle(macro.id).flatMapLatest { cycles ->
            val timeline = TrainingCycleCalculator.buildTimeline(cycles)
            val cycleForWeek = TrainingCycleCalculator.resolveCurrentCycle(timeline, macro, at)
                ?: return@flatMapLatest flowOf(emptyList())
            combine(
                dao.observeCycleGroups(cycleForWeek.entity.id),
                dao.observeSetsForUserInWeek(userName, week.start, week.end),
            ) { cycleGroups, weeklySets ->
                val weeklyStats = SessionProgramProgressCalculator.weeklyStatsByGroup(weeklySets, input.exList)
                SessionProgramProgressCalculator.buildProgressRows(
                    sessionGroupKeys = sessionKeys,
                    weeklyStats = weeklyStats,
                    cycleGroups = cycleGroups,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    /** Objetivos de una sesión cuya semana de programación ya pasó. */
    val programProgressIsPastWeek: StateFlow<Boolean> = session.map { sess ->
        sess != null && WeekBounds.isPastWeek(sess.dateTimestamp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), false)

    private data class SessionProgressInputs(
        val macro: TrainingMacrocycleEntity?,
        val exList: List<ExerciseEntity>,
        val sessionSets: List<SetEntity>,
        val sess: SessionEntity?,
    )

    fun renameSession(newName: String) {
        if (newName.isBlank() || sessionId <= 0L) return
        viewModelScope.launch {
            val current = session.value ?: return@launch
            dao.updateSession(current.copy(customName = newName.trim()))
        }
    }

    fun addExercise(name: String, unitType: UnitType, muscleTags: Collection<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertExercise(
                ExerciseEntity(
                    userName = userName,
                    name = name.trim(),
                    unitType = unitType,
                    muscleTags = ExerciseMuscleTags.toCsv(muscleTags),
                ),
            )
        }
    }

    fun updateExercise(exercise: ExerciseEntity) {
        if (exercise.name.isBlank()) return
        viewModelScope.launch {
            dao.updateExercise(
                exercise.copy(
                    name = exercise.name.trim(),
                    muscleTags = ExerciseMuscleTags.toCsv(ExerciseMuscleTags.fromCsv(exercise.muscleTags)),
                ),
            )
        }
    }

    fun deleteExercise(exercise: ExerciseEntity) {
        viewModelScope.launch { dao.deleteExercise(exercise) }
    }

    fun addSet(exerciseId: Long, reps: Int, weight: Float, rir: Int, note: String) {
        if (exerciseId <= 0L || reps <= 0 || weight <= 0f || sessionId <= 0L) return
        viewModelScope.launch {
            if (dao.countSetsInSession(sessionId) >= MAX_SETS_PER_SESSION) return@launch
            val next = dao.nextSortOrderForSession(sessionId)
            dao.insertSet(
                SetEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    reps = reps,
                    weight = weight,
                    rir = rir,
                    note = note.trim(),
                    sortOrder = next,
                ),
            )
        }
    }

    fun updateSet(set: SetEntity) {
        if (set.exerciseId <= 0L || set.reps <= 0 || set.weight <= 0f) return
        viewModelScope.launch {
            dao.updateSet(set.copy(note = set.note.trim()))
        }
    }

    fun deleteSet(set: SetEntity) {
        viewModelScope.launch { dao.deleteSet(set) }
    }

    fun moveSetUp(set: SetEntity) {
        if (sessionId <= 0L) return
        viewModelScope.launch { moveSetUpAwait(set) }
    }

    fun moveSetDown(set: SetEntity) {
        if (sessionId <= 0L) return
        viewModelScope.launch { moveSetDownAwait(set) }
    }

    suspend fun moveSetUpAwait(set: SetEntity) {
        if (sessionId <= 0L) return
        withContext(Dispatchers.IO) {
            val list = dao.fetchSetsOrderedForSession(sessionId)
            val i = list.indexOfFirst { it.id == set.id }
            if (i <= 0) return@withContext
            swapSortOrder(list[i - 1], list[i])
        }
    }

    suspend fun moveSetDownAwait(set: SetEntity) {
        if (sessionId <= 0L) return
        withContext(Dispatchers.IO) {
            val list = dao.fetchSetsOrderedForSession(sessionId)
            val i = list.indexOfFirst { it.id == set.id }
            if (i < 0 || i >= list.lastIndex) return@withContext
            swapSortOrder(list[i], list[i + 1])
        }
    }

    private suspend fun swapSortOrder(a: SetEntity, b: SetEntity) {
        val oa = a.sortOrder
        val ob = b.sortOrder
        dao.updateSet(a.copy(sortOrder = ob))
        dao.updateSet(b.copy(sortOrder = oa))
    }

    companion object {
        const val MAX_SETS_PER_SESSION = 99
    }
}
