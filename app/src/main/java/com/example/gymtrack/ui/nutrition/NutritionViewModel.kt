package com.example.gymtrack.ui.nutrition

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.dao.OffCatalogDao
import com.example.gymtrack.data.local.entities.BodyMetricEntity
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.data.local.entities.NutritionGoalsEntity
import com.example.gymtrack.domain.ProgressionCalculator.ChartPeriod
import com.example.gymtrack.domain.MacroTotals
import com.example.gymtrack.domain.NutritionActivity
import com.example.gymtrack.domain.NutritionCalculator
import com.example.gymtrack.domain.NutritionGoalType
import com.example.gymtrack.domain.NutritionPlan
import com.example.gymtrack.domain.NutritionSex
import com.example.gymtrack.nutrition.FatSecretClient
import com.example.gymtrack.nutrition.FoodQtyUnit
import com.example.gymtrack.nutrition.FoodSearchRanker
import com.example.gymtrack.nutrition.OffCatalogPrefs
import com.example.gymtrack.nutrition.OffFood
import com.example.gymtrack.nutrition.OffUsedFoods
import com.example.gymtrack.nutrition.OpenFoodFactsApi
import com.example.gymtrack.domain.voice.FuzzyTextNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val dao: GymDao,
    private val catalogDao: OffCatalogDao,
    private val appContext: Context,
    private val userName: String,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _statsPeriod = MutableStateFlow(ChartPeriod.DAILY)
    val statsPeriod: StateFlow<ChartPeriod> = _statsPeriod.asStateFlow()
    fun setStatsPeriod(period: ChartPeriod) { _statsPeriod.value = period }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<OffFood>>(emptyList())
    val searchResults: StateFlow<List<OffFood>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    val catalogCount: StateFlow<Int> =
        catalogDao.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0)

    val customFoods: StateFlow<List<OffFood>> =
        catalogDao.observeCustom()
            .map { rows -> rows.map { it.toOffFood() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    private var searchJob: Job? = null

    init {
        OffUsedFoods.deleteStaleDump(appContext)
    }

    fun setSearchQuery(value: String) { _searchQuery.value = value }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchError.value = null
        _searching.value = false
    }

    fun searchFoods() {
        val q = _searchQuery.value.trim()
        searchJob?.cancel()
        if (q.length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = "Escribe al menos 2 caracteres."
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            _searchError.value = null
            _searchResults.value = emptyList()
            try {
                val cacheOn = OffCatalogPrefs.isEnabled(appContext)
                var merged = emptyList<OffFood>()
                if (cacheOn) {
                    merged = OffUsedFoods.searchLocal(catalogDao, q)
                    _searchResults.value = merged
                }
                val localCodes = merged.map { it.code.ifBlank { it.name } }.toHashSet()
                val live = OpenFoodFactsApi.search(q)
                live.onSuccess { foods ->
                    val online = foods.filter { it.code.ifBlank { it.name } !in localCodes }
                    merged = merged + FoodSearchRanker.rank(q, online)
                    _searchResults.value = merged
                }
                if (merged.size < 12 && FatSecretClient.hasCredentials()) {
                    FatSecretClient.search(q).onSuccess { extra ->
                        val known = merged.map {
                            FuzzyTextNormalizer.normalize("${it.brand} ${it.name}")
                        }.toHashSet()
                        val extraRanked = FoodSearchRanker.rank(
                            q,
                            extra.filter {
                                FuzzyTextNormalizer.normalize("${it.brand} ${it.name}") !in known
                            },
                        )
                        merged = merged + extraRanked
                        _searchResults.value = merged
                    }
                }
                if (merged.isEmpty()) {
                    _searchError.value = if (live.isFailure) {
                        "Sin conexión. Comprueba internet para buscar en Open Food Facts."
                    } else {
                        "Sin alimentos con macros para esa búsqueda."
                    }
                } else if (live.isFailure && cacheOn) {
                    _searchError.value = "Sin internet. Mostrando alimentos que ya usaste."
                }
            } finally {
                _searching.value = false
            }
        }
    }

    suspend fun loadDetails(food: OffFood): OffFood {
        if (!food.needsDetails) return food
        return FatSecretClient.details(food).getOrDefault(food)
    }

    val goals: StateFlow<NutritionGoalsEntity?> =
        dao.observeNutritionGoals(userName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    val dayLogs: StateFlow<List<FoodLogEntity>> =
        _selectedDate.flatMapLatest { date ->
            dao.observeFoodLogsForDay(userName, date.toDayStartMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    val dayTotals: StateFlow<MacroTotals> =
        dayLogs.map { logs -> logs.toTotals() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), MacroTotals())

    val periodLogs: StateFlow<List<FoodLogEntity>> =
        combine(_selectedDate, _statsPeriod) { date, period -> date to period }
            .flatMapLatest { (date, period) ->
                val (from, to) = periodRange(date, period)
                dao.observeFoodLogsInRange(userName, from, to)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    val periodTotals: StateFlow<MacroTotals> =
        periodLogs.map { it.toTotals() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), MacroTotals())

    val bodyMetrics: StateFlow<List<BodyMetricEntity>> =
        dao.observeBodyMetrics(userName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    fun shiftDay(delta: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(delta)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun addFood(
        food: OffFood,
        quantity: Float,
        unit: FoodQtyUnit,
        saveAsCustom: Boolean = false,
    ) {
        if (quantity <= 0f) return
        if (unit !in food.units) return
        val portion = food.macrosFor(quantity, unit)
        val kcal = NutritionCalculator.kcalFromMacros(portion.protein, portion.carbs, portion.fat).toFloat()
        viewModelScope.launch {
            val stored = if (saveAsCustom || food.isCustom) {
                OffUsedFoods.saveCustom(catalogDao, food)
            } else {
                food
            }
            dao.insertFoodLog(
                FoodLogEntity(
                    userName = userName,
                    dayStartMillis = _selectedDate.value.toDayStartMillis(),
                    offCode = stored.code,
                    name = stored.name,
                    brand = stored.brand,
                    quantity = quantity,
                    unit = unit.code,
                    grams = portion.grams,
                    kcal = kcal,
                    protein = portion.protein,
                    carbs = portion.carbs,
                    fat = portion.fat,
                    fiber = portion.fiber,
                ),
            )
            if (!stored.isCustom && OffCatalogPrefs.isEnabled(appContext)) {
                OffUsedFoods.remember(catalogDao, stored)
            }
        }
    }

    fun updateFood(log: FoodLogEntity, food: OffFood, quantity: Float, unit: FoodQtyUnit) {
        if (quantity <= 0f) return
        if (unit !in food.units) return
        val portion = food.macrosFor(quantity, unit)
        val kcal = NutritionCalculator.kcalFromMacros(portion.protein, portion.carbs, portion.fat).toFloat()
        viewModelScope.launch {
            dao.updateFoodLog(
                log.copy(
                    name = food.name,
                    quantity = quantity,
                    unit = unit.code,
                    grams = portion.grams,
                    kcal = kcal,
                    protein = portion.protein,
                    carbs = portion.carbs,
                    fat = portion.fat,
                    fiber = portion.fiber,
                ),
            )
        }
    }

    fun deleteFood(log: FoodLogEntity) {
        viewModelScope.launch { dao.deleteFoodLog(log) }
    }

    fun saveGoals(
        sex: NutritionSex,
        age: Int,
        heightCm: Float,
        weightKg: Float,
        activity: NutritionActivity,
        goal: NutritionGoalType,
        bodyFatPercent: Float?,
        proteinG: Float,
        carbsG: Float,
        fatG: Float,
        fiberG: Float,
    ): NutritionPlan {
        val plan = NutritionCalculator.plan(sex, age, heightCm, weightKg, activity, goal, bodyFatPercent)
        val calories = NutritionCalculator.kcalFromMacros(proteinG, carbsG, fatG)
        viewModelScope.launch {
            dao.upsertNutritionGoals(
                NutritionGoalsEntity(
                    userName = userName,
                    sex = sex.name,
                    age = age,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    activity = activity.name,
                    goal = goal.name,
                    tdee = plan.tdee,
                    calories = calories,
                    proteinG = proteinG,
                    carbsG = carbsG,
                    fatG = fatG,
                    fiberG = fiberG,
                ),
            )
        }
        return plan.copy(
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            fiberG = fiberG,
        )
    }

    fun saveBodyMetric(weightKg: Float, bodyFatPercent: Float) {
        viewModelScope.launch {
            dao.upsertBodyMetric(
                BodyMetricEntity(
                    userName = userName,
                    dayStartMillis = _selectedDate.value.toDayStartMillis(),
                    weightKg = weightKg,
                    bodyFatPercent = bodyFatPercent,
                ),
            )
        }
    }

    private fun LocalDate.toDayStartMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    private fun periodRange(date: LocalDate, period: ChartPeriod): Pair<Long, Long> {
        val (start, endExclusive) = when (period) {
            ChartPeriod.DAILY -> date to date.plusDays(1)
            ChartPeriod.WEEKLY -> {
                val monday = date.with(java.time.DayOfWeek.MONDAY)
                monday to monday.plusWeeks(1)
            }
            ChartPeriod.MONTHLY -> {
                val first = date.withDayOfMonth(1)
                first to first.plusMonths(1)
            }
            ChartPeriod.ANNUAL -> {
                val first = date.withDayOfYear(1)
                first to first.plusYears(1)
            }
        }
        return start.toDayStartMillis() to endExclusive.toDayStartMillis()
    }

    private fun List<FoodLogEntity>.toTotals() = fold(MacroTotals()) { acc, log ->
        acc + MacroTotals(log.kcal, log.protein, log.carbs, log.fat, log.fiber)
    }
}

fun NutritionGoalsEntity.asTargets() = MacroTotals(
    kcal = calories.toFloat(),
    protein = proteinG,
    carbs = carbsG,
    fat = fatG,
    fiber = fiberG,
)

fun FoodLogEntity.toOffFood(): OffFood {
    val qty = quantity.takeIf { it > 0f } ?: 1f
    val unit = FoodQtyUnit.entries.find { it.code == this.unit } ?: FoodQtyUnit.GRAMS
    val per100 = if (grams > 0f) 100f / grams else 0f
    return OffFood(
        code = offCode,
        name = name,
        brand = brand,
        kcal100g = if (per100 > 0f) kcal * per100 else 0f,
        protein100g = if (per100 > 0f) protein * per100 else 0f,
        carbs100g = if (per100 > 0f) carbs * per100 else 0f,
        fat100g = if (per100 > 0f) fat * per100 else 0f,
        fiber100g = if (per100 > 0f) fiber * per100 else 0f,
        kcal100ml = if (unit == FoodQtyUnit.MILLILITERS) kcal / qty * 100f else null,
        servingGrams = if (unit == FoodQtyUnit.SERVING) grams / qty else null,
        servingLabel = if (unit == FoodQtyUnit.SERVING) "ración" else null,
        quantityHint = "",
        isGeneric = brand.isBlank(),
        needsDetails = false,
        cacheable = false,
        isCustom = offCode.startsWith("custom-"),
        servingKcal = if (unit == FoodQtyUnit.SERVING) kcal / qty else null,
        servingProtein = if (unit == FoodQtyUnit.SERVING) protein / qty else null,
        servingCarbs = if (unit == FoodQtyUnit.SERVING) carbs / qty else null,
        servingFat = if (unit == FoodQtyUnit.SERVING) fat / qty else null,
        servingFiber = if (unit == FoodQtyUnit.SERVING) fiber / qty else null,
    )
}
