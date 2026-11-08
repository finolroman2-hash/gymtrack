package com.example.gymtrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.ForecastPointEntity
import com.example.gymtrack.data.local.entities.SetProgressWithSession
import com.example.gymtrack.data.local.entities.UnitType
import com.example.gymtrack.domain.ExerciseMuscleTags
import com.example.gymtrack.domain.ForecastCalculator
import com.example.gymtrack.domain.ProgramProgressPeriodCalculator
import com.example.gymtrack.domain.ProgressionCalculator
import com.example.gymtrack.domain.ProgressionCalculator.ChartPeriod
import com.example.gymtrack.domain.SessionGroupProgress
import com.example.gymtrack.domain.TrainingCycleCalculator
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AdvancedProgramUiState(
    val progress: List<SessionGroupProgress> = emptyList(),
    val periodNote: String? = null,
)

data class ProgressPoint(
    val x: Float,
    val y: Float,
    val raw: SetProgressWithSession
)

class DashboardViewModel(
    private val dao: GymDao,
    private val userName: String,
    initialExerciseId: Long? = null,
) : ViewModel() {
    private val _selectedExerciseId = MutableStateFlow<Long?>(initialExerciseId)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()
    fun setExercise(id: Long?) {
        _selectedExerciseId.value = id
        if (id != null) _selectedMuscleGroupKey.value = null
    }

    /** Comparar todos los ejercicios cuya etiqueta principal coincide (excluye con ejercicio suelto). */
    private val _selectedMuscleGroupKey = MutableStateFlow<String?>(null)
    val selectedMuscleGroupKey: StateFlow<String?> = _selectedMuscleGroupKey.asStateFlow()
    fun setMuscleGroup(key: String?) {
        _selectedMuscleGroupKey.value = key
        if (key != null) _selectedExerciseId.value = null
    }

    // ── Session mode: null = General, Long = specific session ─────────────────
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()
    fun setSession(id: Long?) {
        _selectedSessionId.value = id
        _selectedSessionIds.value = emptyList()
    }

    private val _selectedSessionIds = MutableStateFlow<List<Long>>(emptyList())
    val selectedSessionIds: StateFlow<List<Long>> = _selectedSessionIds.asStateFlow()
    fun setSessionComparison(ids: Collection<Long>) {
        _selectedSessionIds.value = ids.distinct()
        _selectedSessionId.value = null
    }

    // ── Chart period (General: filtra filas; Sesión: sin efecto en filas) ───────
    private val _chartPeriod = MutableStateFlow(ChartPeriod.WEEKLY)
    val chartPeriod: StateFlow<ChartPeriod> = _chartPeriod.asStateFlow()
    fun setChartPeriod(p: ChartPeriod) { _chartPeriod.value = p }

    // ── Estadística de macrociclo (pronóstico + progreso vs programación) ────
    private val _fullViewMode = MutableStateFlow(false)
    val fullViewMode: StateFlow<Boolean> = _fullViewMode.asStateFlow()
    fun setFullViewMode(enabled: Boolean) {
        _fullViewMode.value = enabled
    }

    val forecastPoints: StateFlow<List<ForecastPointEntity>> =
        _selectedExerciseId
            .flatMapLatest { exId ->
                if (exId == null) flowOf(emptyList())
                else dao.observeForecastPoints(userName, exId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    // ── Raw rows: un ejercicio, varios del mismo grupo muscular, o vacío ───────
    private val allRows: StateFlow<List<SetProgressWithSession>> =
        combine(
            _selectedExerciseId,
            _selectedSessionId,
            _selectedSessionIds,
            _selectedMuscleGroupKey,
            dao.observeExercises(userName),
        ) { exId, sessId, sessIds, groupKey, exList ->
            Selection(exId, sessId, sessIds, groupKey) to exList
        }
            .flatMapLatest { (sel, exList) ->
                val compareSessionIds = sel.sessionIds
                when {
                    sel.groupKey != null -> {
                        val ids = exList
                            .filter { ExerciseMuscleTags.primaryGroupKey(it.muscleTags) == sel.groupKey }
                            .map { it.id }
                        when {
                            ids.isEmpty() -> flowOf(emptyList())
                            compareSessionIds.isNotEmpty() ->
                                dao.observeProgressForExerciseIdsInSessions(compareSessionIds, ids)
                            sel.sessionId != null -> dao.observeProgressForExerciseIdsInSession(sel.sessionId, ids)
                            else -> dao.observeProgressForExerciseIds(userName, ids)
                        }
                    }
                    sel.exerciseId == null -> flowOf(emptyList())
                    compareSessionIds.isNotEmpty() ->
                        dao.observeProgressBySessions(sel.exerciseId, compareSessionIds)
                    sel.sessionId != null -> dao.observeProgressBySession(sel.exerciseId, sel.sessionId)
                    else -> dao.observeProgressAllWithSession(userName, sel.exerciseId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    // ── Period-filtered rows ───────────────────────────────────────────────────
    private fun SetProgressWithSession.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(sessionDate).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Filas usadas en gráfica y resúmenes: en General, filtro por pestaña de período; en sesión, toda la sesión. */
    private val periodRows: StateFlow<List<SetProgressWithSession>> =
        combine(allRows, _chartPeriod, _selectedSessionId, _selectedSessionIds) { rows, period, sessId, sessIds ->
            if (sessId != null || sessIds.isNotEmpty()) rows
            else when (period) {
                ChartPeriod.DAILY -> {
                    val today = LocalDate.now()
                    rows.filter { it.toLocalDate() == today }
                }
                ChartPeriod.WEEKLY -> {
                    val cutoff = LocalDate.now().minusDays(6)
                    rows.filter { it.toLocalDate() >= cutoff }
                }
                ChartPeriod.MONTHLY -> {
                    val now = LocalDate.now()
                    rows.filter { val d = it.toLocalDate(); d.year == now.year && d.month == now.month }
                }
                ChartPeriod.ANNUAL -> {
                    val year = LocalDate.now().year
                    rows.filter { it.toLocalDate().year == year }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    val advancedProgramProgress: StateFlow<AdvancedProgramUiState> =
        combine(
            combine(
                _fullViewMode,
                _selectedExerciseId,
                _selectedMuscleGroupKey,
                _chartPeriod,
                _selectedSessionId,
            ) { enabled, exerciseId, groupKey, period, sessionId ->
                AdvancedInputsHead(enabled, exerciseId, groupKey, period, sessionId)
            },
            combine(
                _selectedSessionIds,
                periodRows,
                dao.observeExercises(userName),
                dao.observeSessions(userName),
            ) { sessionIds, rows, exercises, sessions ->
                AdvancedInputsTail(sessionIds, rows, exercises, sessions)
            },
        ) { head, tail ->
            AdvancedInputs(
                enabled = head.enabled,
                exerciseId = head.exerciseId,
                groupKey = head.groupKey,
                period = head.period,
                sessionId = head.sessionId,
                sessionIds = tail.sessionIds,
                rows = tail.rows,
                exercises = tail.exercises,
                sessions = tail.sessions,
            )
        }.flatMapLatest { input ->
            if (!input.enabled) return@flatMapLatest flowOf(AdvancedProgramUiState())
            val muscleTags = input.exerciseId?.let { id ->
                input.exercises.find { it.id == id }?.muscleTags
            }
            val resolvedGroup = ProgramProgressPeriodCalculator.resolveMuscleGroupKey(
                input.groupKey,
                muscleTags,
            ) ?: return@flatMapLatest flowOf(AdvancedProgramUiState())
            val isComparison = input.sessionIds.isNotEmpty()
            val isGeneral = input.sessionId == null && !isComparison
            val comparisonDates = input.sessionIds.mapNotNull { sid ->
                input.sessions.find { it.id == sid }?.dateTimestamp?.let { ts ->
                    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            val singleSessionDate = input.sessionId?.let { sid ->
                input.sessions.find { it.id == sid }?.dateTimestamp?.let { ts ->
                    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            dao.observeActiveMacrocycle(userName).flatMapLatest { macro ->
                if (macro == null || !macro.isActive || macro.activatedAt <= 0L) {
                    return@flatMapLatest flowOf(AdvancedProgramUiState())
                }
                combine(
                    dao.observeCyclesForMacrocycle(macro.id),
                    dao.observeCycleGroupsForMacrocycle(macro.id),
                ) { cycles, allGroups ->
                    cycles to allGroups
                }.map { (cycles, allGroups) ->
                    val timeline = TrainingCycleCalculator.buildTimeline(cycles)
                    val groupsByCycle = ProgramProgressPeriodCalculator.groupsByCycleId(allGroups)
                    val macroRows = input.rows.filter { it.sessionDate >= macro.activatedAt }
                    val scope = ProgramProgressPeriodCalculator.computeScope(
                        period = input.period,
                        isGeneral = isGeneral,
                        isComparison = isComparison,
                        comparisonSessionDates = comparisonDates,
                        sessionDate = singleSessionDate,
                        macro = macro,
                        timeline = timeline,
                        groupKey = resolvedGroup,
                        groupsByCycle = groupsByCycle,
                    )
                    if (scope == null) {
                        AdvancedProgramUiState(
                            periodNote = "El grupo no está configurado en los ciclos del periodo seleccionado.",
                        )
                    } else {
                        val setCount = macroRows.size
                        val avgRir = if (macroRows.isEmpty()) {
                            0f
                        } else {
                            macroRows.map { it.rir }.average().toFloat()
                        }
                        val bar = ProgramProgressPeriodCalculator.buildDashboardProgress(
                            groupKey = resolvedGroup,
                            setCount = setCount,
                            avgRir = avgRir,
                            scope = scope,
                        )
                        AdvancedProgramUiState(
                            progress = listOf(bar),
                            periodNote = scope.periodNote,
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), AdvancedProgramUiState())

    /** Orden cronológico: primera serie del período → última. */
    val points: StateFlow<List<ProgressPoint>> = periodRows.map { rows ->
        rows.map { r ->
            ProgressPoint(r.sessionDate.toFloat(), ProgressionCalculator.estimateOneRm(r.weight, r.reps, r.rir), r)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    /** Líneas de pronóstico superpuestas (65 % opacidad) sobre la gráfica normal. */
    val forecastOverlayLines: StateFlow<List<List<com.patrykandpatrick.vico.core.entry.FloatEntry>>> =
        combine(points, forecastPoints, _fullViewMode, _selectedExerciseId) { pts, fc, mode, exId ->
            if (!mode || fc.isEmpty() || exId == null || pts.isEmpty()) {
                emptyList()
            } else {
                ForecastCalculator.buildForecastOverlayLines(pts, fc)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    val topPerformanceSets: StateFlow<List<ProgressPoint>> = points.map { pts ->
        pts
            .sortedByDescending { it.y }
            .distinctBy { it.raw.setId }
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    val sessionBands: StateFlow<List<ChartSessionBand>> = points.map { pts ->
        computeSessionBands(pts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())

    /** Series contadas en el mismo conjunto que la gráfica (período o sesión). */
    val totalSetsInSelectedScope: StateFlow<Int> =
        periodRows.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), 0)

    /** RIR medio solo del ámbito mostrado (período seleccionado en General, o sesión en modo sesión). */
    val avgRirInSelectedScope: StateFlow<Float?> = periodRows.map { rows ->
        if (rows.isEmpty()) null else rows.map { it.rir }.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), null)

    /** Mejor 1RM estimado en el mismo ámbito que la gráfica. */
    val bestOneRmInSelectedScope: StateFlow<Float?> = periodRows.map { rows ->
        rows.maxOfOrNull { ProgressionCalculator.estimateOneRm(it.weight, it.reps, it.rir) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), null)

    /** Unidad de peso para mostrar el 1RM (primer punto del ámbito). */
    val weightUnitForSelectedScope: StateFlow<UnitType> = periodRows.map { rows ->
        rows.firstOrNull()?.unitType ?: UnitType.KG
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), UnitType.KG)

    private data class Selection(
        val exerciseId: Long?,
        val sessionId: Long?,
        val sessionIds: List<Long>,
        val groupKey: String?,
    )

    private data class AdvancedInputsHead(
        val enabled: Boolean,
        val exerciseId: Long?,
        val groupKey: String?,
        val period: ChartPeriod,
        val sessionId: Long?,
    )

    private data class AdvancedInputsTail(
        val sessionIds: List<Long>,
        val rows: List<SetProgressWithSession>,
        val exercises: List<com.example.gymtrack.data.local.entities.ExerciseEntity>,
        val sessions: List<com.example.gymtrack.data.local.entities.SessionEntity>,
    )

    private data class AdvancedInputs(
        val enabled: Boolean,
        val exerciseId: Long?,
        val groupKey: String?,
        val period: ChartPeriod,
        val sessionId: Long?,
        val sessionIds: List<Long>,
        val rows: List<SetProgressWithSession>,
        val exercises: List<com.example.gymtrack.data.local.entities.ExerciseEntity>,
        val sessions: List<com.example.gymtrack.data.local.entities.SessionEntity>,
    )
}
