package com.example.gymtrack.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.entities.BodyMetricEntity
import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.data.local.entities.ForecastPointEntity
import com.example.gymtrack.data.local.entities.NutritionGoalsEntity
import com.example.gymtrack.data.local.entities.ProfileEntity
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SetEntity
import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.data.local.entities.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BackupManager {

    private val duplicateMutex = Mutex()

    private const val TAG = "BackupManager"
    private const val BACKUP_VERSION = 2

    // ─── Exportar ─────────────────────────────────────────────────────────────

    suspend fun accountShareJson(dao: GymDao, userName: String): JSONObject =
        withContext(Dispatchers.IO) { buildBackupJson(dao, userName) }

    /**
     * Serializa toda la BD del usuario en JSON y escribe en el URI elegido (SAF).
     * Retorna true si todo fue bien.
     */
    suspend fun exportToUri(
        context: Context,
        destinationUri: Uri,
        dao: GymDao,
        userName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = buildBackupJson(dao, userName)
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }

    private suspend fun buildBackupJson(dao: GymDao, userName: String): JSONObject {
        val root = JSONObject()
        root.put("backup_version", BACKUP_VERSION)
        root.put("gymtrack_kind", GymTrackShare.KIND_ACCOUNT)
        root.put("exported_at", System.currentTimeMillis())
        root.put("user_name", userName)

        // Profile
        root.put("profile", JSONObject().apply {
            put("name", userName)
        })

        // Exercises
        val exercisesArray = JSONArray()
        dao.observeExercises(userName).first().forEach { ex ->
            exercisesArray.put(JSONObject().apply {
                put("id", ex.id)
                put("userName", ex.userName)
                put("name", ex.name)
                put("unitType", ex.unitType.name)  // "KG" o "PLATES"
                put("muscleTags", ex.muscleTags)
            })
        }
        root.put("exercises", exercisesArray)

        // Sessions + sets
        val sessionsArray = JSONArray()
        dao.observeSessions(userName).first().forEach { session ->
            val setsArray = JSONArray()
            dao.observeSetsBySession(session.id).first().forEach { set ->
                setsArray.put(JSONObject().apply {
                    put("id", set.id)
                    put("exerciseId", set.exerciseId)
                    put("reps", set.reps)
                    put("weight", set.weight)
                    put("rir", set.rir)
                    put("note", set.note)
                    put("createdAt", set.createdAt)
                    put("sortOrder", set.sortOrder)
                })
            }
            sessionsArray.put(JSONObject().apply {
                put("id", session.id)
                put("userName", session.userName)
                put("customName", session.customName)
                put("dateTimestamp", session.dateTimestamp)
                put("sets", setsArray)
            })
        }
        root.put("sessions", sessionsArray)

        // Pronósticos de 1RM
        val forecastArray = JSONArray()
        dao.observeExercises(userName).first().forEach { ex ->
            dao.observeForecastPoints(userName, ex.id).first().forEach { point ->
                forecastArray.put(JSONObject().apply {
                    put("exerciseId", ex.id)
                    put("dateMillis", point.dateMillis)
                    put("predictedOneRm", point.predictedOneRm.toDouble())
                })
            }
        }
        root.put("forecast_points", forecastArray)

        val macrosArray = JSONArray()
        dao.observeMacrocycles(userName).first().forEach { macro ->
            macrosArray.put(buildMacrocycleObject(dao, macro))
        }
        root.put("macrocycles", macrosArray)

        val foodArray = JSONArray()
        dao.observeAllFoodLogs(userName).first().forEach { log ->
            foodArray.put(JSONObject().apply {
                put("dayStartMillis", log.dayStartMillis)
                put("offCode", log.offCode)
                put("name", log.name)
                put("brand", log.brand)
                put("quantity", log.quantity.toDouble())
                put("unit", log.unit)
                put("grams", log.grams.toDouble())
                put("kcal", log.kcal.toDouble())
                put("protein", log.protein.toDouble())
                put("carbs", log.carbs.toDouble())
                put("fat", log.fat.toDouble())
                put("fiber", log.fiber.toDouble())
                put("createdAt", log.createdAt)
            })
        }
        root.put("food_logs", foodArray)

        dao.observeNutritionGoals(userName).first()?.let { goals ->
            root.put("nutrition_goals", JSONObject().apply {
                put("sex", goals.sex)
                put("age", goals.age)
                put("heightCm", goals.heightCm.toDouble())
                put("weightKg", goals.weightKg.toDouble())
                put("activity", goals.activity)
                put("goal", goals.goal)
                put("tdee", goals.tdee)
                put("calories", goals.calories)
                put("proteinG", goals.proteinG.toDouble())
                put("carbsG", goals.carbsG.toDouble())
                put("fatG", goals.fatG.toDouble())
                put("fiberG", goals.fiberG.toDouble())
                put("updatedAt", goals.updatedAt)
            })
        }

        val metricsArray = JSONArray()
        dao.observeBodyMetrics(userName).first().forEach { metric ->
            metricsArray.put(JSONObject().apply {
                put("dayStartMillis", metric.dayStartMillis)
                put("weightKg", metric.weightKg.toDouble())
                put("bodyFatPercent", metric.bodyFatPercent.toDouble())
            })
        }
        root.put("body_metrics", metricsArray)

        return root
    }

    // ─── Importar ─────────────────────────────────────────────────────────────

    sealed class ImportResult {
        data class Success(val importedUserName: String? = null) : ImportResult()
        data class Error(val reason: String) : ImportResult()
    }

    private suspend fun buildMacrocycleObject(
        dao: GymDao,
        macro: TrainingMacrocycleEntity,
    ): JSONObject {
        val cyclesArray = JSONArray()
        dao.observeCyclesForMacrocycle(macro.id).first().forEach { cycle ->
            val groupsArray = JSONArray()
            dao.observeCycleGroups(cycle.id).first().forEach { group ->
                groupsArray.put(JSONObject().apply {
                    put("muscleGroupKey", group.muscleGroupKey)
                    put("avgRir", group.avgRir.toDouble())
                    put("weeklyVolume", group.weeklyVolume.toDouble())
                })
            }
            cyclesArray.put(JSONObject().apply {
                put("id", cycle.id)
                put("sortOrder", cycle.sortOrder)
                put("weeks", cycle.weeks)
                put("avgRir", cycle.avgRir.toDouble())
                put("weeklyVolume", cycle.weeklyVolume.toDouble())
                put("label", cycle.label)
                put("groups", groupsArray)
            })
        }
        return JSONObject().apply {
            put("id", macro.id)
            put("name", macro.name)
            put("createdAt", macro.createdAt)
            put("isActive", false)
            put("activatedAt", 0L)
            put("startCycleId", 0L)
            put("startWeekInCycle", 1)
            put("startDayInWeek", 1)
            put("cycles", cyclesArray)
        }
    }

    suspend fun buildMacrocycleShareJson(
        dao: GymDao,
        userName: String,
        macroId: Long,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val macro = dao.observeMacrocycles(userName).first().find { it.id == macroId } ?: return@withContext null
        JSONObject().apply {
            put("gymtrack_kind", GymTrackShare.KIND_MACROCYCLE)
            put("payload_version", 1)
            put("exported_at", System.currentTimeMillis())
            put("macrocycle", buildMacrocycleObject(dao, macro))
        }
    }

    suspend fun importMacrocycleForUser(
        dao: GymDao,
        userName: String,
        shareJson: JSONObject,
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val macroObj = shareJson.optJSONObject("macrocycle")
                ?: return@withContext ImportResult.Error("El archivo no contiene un macrociclo.")
            val wrapper = JSONObject().put("macrocycles", JSONArray().put(macroObj))
            restoreMacrocyclesFromJson(wrapper, dao, userName, forceInactive = true)
            ImportResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Import macrocycle failed", e)
            ImportResult.Error("No se pudo añadir el macrociclo.")
        }
    }

    suspend fun importSharedAccount(
        dao: GymDao,
        shareJson: JSONObject,
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val desired = shareJson.optString("user_name").ifBlank { "Cuenta" }
            val existing = dao.observeProfiles().first().map { it.name }
            val unique = if (existing.any { it.equals(desired, ignoreCase = true) }) {
                suggestDuplicateName(existing, desired)
            } else {
                desired
            }
            shareJson.put("user_name", unique)
            shareJson.put("profile", JSONObject().put("name", unique))
            restoreFromJson(shareJson, dao)
            ImportResult.Success(unique)
        } catch (e: Exception) {
            Log.e(TAG, "Import shared account failed", e)
            ImportResult.Error("No se pudo añadir la cuenta.")
        }
    }

    /**
     * Lee el JSON del URI, valida su estructura y restaura la BD.
     * Borra primero los datos del usuario importado para evitar duplicados.
     * Nunca lanza excepción; siempre retorna ImportResult.
     */
    suspend fun importFromUri(
        context: Context,
        sourceUri: Uri,
        dao: GymDao
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(sourceUri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: return@withContext ImportResult.Error("No se pudo leer el archivo.")

            val root = parseAndValidate(raw)
                ?: return@withContext ImportResult.Error(
                    "El archivo no es un backup válido de GymTrack."
                )

            restoreFromJson(root, dao)
            ImportResult.Success()

        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.Error("Error inesperado: ${e.localizedMessage}")
        }
    }

    /** Valida campos mínimos. Retorna null si el JSON está corrupto o incompleto. */
    private fun parseAndValidate(raw: String): JSONObject? {
        return try {
            if (raw.isBlank()) return null
            val json = JSONObject(raw)
            if (!json.has("backup_version")) return null
            if (!json.has("user_name"))      return null
            if (!json.has("exercises"))      return null
            if (!json.has("sessions"))       return null
            // Versión soportada
            if (json.getInt("backup_version") > BACKUP_VERSION) return null
            json
        } catch (e: JSONException) {
            null
        }
    }

    private suspend fun restoreFromJson(root: JSONObject, dao: GymDao) {
        val userName = root.getString("user_name")

        // Limpiar datos previos del usuario
        dao.deleteAccountData(userName)

        // Insertar perfil
        dao.insertProfile(ProfileEntity(name = userName, createdAt = System.currentTimeMillis()))

        // Ejercicios — guardamos mapa id_original → id_nuevo
        val exerciseIdMap = mutableMapOf<Long, Long>()
        val exercisesArray = root.getJSONArray("exercises")
        for (i in 0 until exercisesArray.length()) {
            val obj      = exercisesArray.getJSONObject(i)
            val origId   = obj.getLong("id")
            val unitType = try { UnitType.valueOf(obj.getString("unitType")) } catch (e: Exception) { UnitType.KG }
            val newId = dao.insertExercise(
                ExerciseEntity(
                    userName = userName,
                    name     = obj.getString("name"),
                    unitType = unitType,
                    muscleTags = obj.optString("muscleTags", ""),
                )
            )
            exerciseIdMap[origId] = newId
        }

        // Sesiones + sets
        val sessionsArray = root.getJSONArray("sessions")
        for (i in 0 until sessionsArray.length()) {
            val sObj     = sessionsArray.getJSONObject(i)
            val newSessId = dao.insertSession(
                SessionEntity(
                    userName      = userName,
                    customName    = sObj.getString("customName"),
                    dateTimestamp = sObj.getLong("dateTimestamp")
                )
            )
            val setsArray = sObj.optJSONArray("sets") ?: JSONArray()
            for (j in 0 until setsArray.length()) {
                val setObj    = setsArray.getJSONObject(j)
                val origExId  = setObj.getLong("exerciseId")
                val newExId   = exerciseIdMap[origExId] ?: continue   // ignora sets huérfanos
                dao.insertSet(
                    SetEntity(
                        sessionId  = newSessId,
                        exerciseId = newExId,
                        reps       = setObj.getInt("reps"),
                        weight     = setObj.getDouble("weight").toFloat(),
                        rir        = setObj.getInt("rir"),
                        note       = setObj.optString("note", ""),
                        createdAt  = setObj.optLong("createdAt", System.currentTimeMillis()),
                        sortOrder  = setObj.optInt("sortOrder", 0),
                    )
                )
            }
        }

        restoreForecastFromJson(root, dao, userName, exerciseIdMap)
        restoreMacrocyclesFromJson(root, dao, userName)
        restoreNutritionFromJson(root, dao, userName)
    }

    private suspend fun restoreNutritionFromJson(root: JSONObject, dao: GymDao, userName: String) {
        val foodArray = root.optJSONArray("food_logs") ?: JSONArray()
        for (i in 0 until foodArray.length()) {
            val obj = foodArray.getJSONObject(i)
            dao.insertFoodLog(
                FoodLogEntity(
                    userName = userName,
                    dayStartMillis = obj.getLong("dayStartMillis"),
                    offCode = obj.optString("offCode", ""),
                    name = obj.getString("name"),
                    brand = obj.optString("brand", ""),
                    quantity = obj.getDouble("quantity").toFloat(),
                    unit = obj.getString("unit"),
                    grams = obj.getDouble("grams").toFloat(),
                    kcal = obj.getDouble("kcal").toFloat(),
                    protein = obj.getDouble("protein").toFloat(),
                    carbs = obj.getDouble("carbs").toFloat(),
                    fat = obj.getDouble("fat").toFloat(),
                    fiber = obj.optDouble("fiber", 0.0).toFloat(),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
        }
        root.optJSONObject("nutrition_goals")?.let { obj ->
            dao.upsertNutritionGoals(
                NutritionGoalsEntity(
                    userName = userName,
                    sex = obj.getString("sex"),
                    age = obj.getInt("age"),
                    heightCm = obj.getDouble("heightCm").toFloat(),
                    weightKg = obj.getDouble("weightKg").toFloat(),
                    activity = obj.getString("activity"),
                    goal = obj.getString("goal"),
                    tdee = obj.getInt("tdee"),
                    calories = obj.getInt("calories"),
                    proteinG = obj.getDouble("proteinG").toFloat(),
                    carbsG = obj.getDouble("carbsG").toFloat(),
                    fatG = obj.getDouble("fatG").toFloat(),
                    fiberG = obj.getDouble("fiberG").toFloat(),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
        }
        val metricsArray = root.optJSONArray("body_metrics") ?: JSONArray()
        for (i in 0 until metricsArray.length()) {
            val obj = metricsArray.getJSONObject(i)
            dao.upsertBodyMetric(
                BodyMetricEntity(
                    userName = userName,
                    dayStartMillis = obj.getLong("dayStartMillis"),
                    weightKg = obj.getDouble("weightKg").toFloat(),
                    bodyFatPercent = obj.getDouble("bodyFatPercent").toFloat(),
                ),
            )
        }
    }

    private suspend fun restoreForecastFromJson(
        root: JSONObject,
        dao: GymDao,
        userName: String,
        exerciseIdMap: Map<Long, Long>,
    ) {
        val forecastArray = root.optJSONArray("forecast_points") ?: return
        val points = mutableListOf<ForecastPointEntity>()
        for (i in 0 until forecastArray.length()) {
            val obj = forecastArray.getJSONObject(i)
            val newExId = exerciseIdMap[obj.getLong("exerciseId")] ?: continue
            points.add(
                ForecastPointEntity(
                    userName = userName,
                    exerciseId = newExId,
                    dateMillis = obj.getLong("dateMillis"),
                    predictedOneRm = obj.getDouble("predictedOneRm").toFloat(),
                ),
            )
        }
        if (points.isNotEmpty()) {
            dao.insertForecastPoints(points)
        }
    }

    private suspend fun restoreMacrocyclesFromJson(
        root: JSONObject,
        dao: GymDao,
        userName: String,
        forceInactive: Boolean = false,
    ) {
        val macrosArray = root.optJSONArray("macrocycles") ?: return
        val macroIdMap = mutableMapOf<Long, Long>()
        val cycleIdMap = mutableMapOf<Long, Long>()
        val macroStartCycleIds = mutableMapOf<Long, Long>()

        for (i in 0 until macrosArray.length()) {
            val mObj = macrosArray.getJSONObject(i)
            val origMacroId = mObj.getLong("id")
            val newMacroId = dao.insertMacrocycle(
                TrainingMacrocycleEntity(
                    userName = userName,
                    name = mObj.getString("name"),
                    createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
                    isActive = if (forceInactive) false else mObj.optBoolean("isActive", false),
                    activatedAt = if (forceInactive) 0L else mObj.optLong("activatedAt", 0L),
                    startCycleId = 0L,
                    startWeekInCycle = mObj.optInt("startWeekInCycle", 1),
                    startDayInWeek = mObj.optInt("startDayInWeek", 1),
                ),
            )
            macroIdMap[origMacroId] = newMacroId
            if (!forceInactive && mObj.optLong("startCycleId", 0L) > 0L) {
                macroStartCycleIds[origMacroId] = mObj.getLong("startCycleId")
            }

            val cyclesArray = mObj.optJSONArray("cycles") ?: JSONArray()
            for (j in 0 until cyclesArray.length()) {
                val cObj = cyclesArray.getJSONObject(j)
                val origCycleId = cObj.getLong("id")
                val newCycleId = dao.insertTrainingCycle(
                    TrainingCycleEntity(
                        macrocycleId = newMacroId,
                        userName = userName,
                        sortOrder = cObj.getInt("sortOrder"),
                        weeks = cObj.getInt("weeks"),
                        avgRir = cObj.getDouble("avgRir").toFloat(),
                        weeklyVolume = cObj.getDouble("weeklyVolume").toFloat(),
                        label = cObj.optString("label", ""),
                    ),
                )
                cycleIdMap[origCycleId] = newCycleId

                val groupsArray = cObj.optJSONArray("groups") ?: JSONArray()
                for (k in 0 until groupsArray.length()) {
                    val gObj = groupsArray.getJSONObject(k)
                    dao.upsertCycleGroup(
                        TrainingCycleGroupEntity(
                            cycleId = newCycleId,
                            muscleGroupKey = gObj.getString("muscleGroupKey"),
                            avgRir = gObj.getDouble("avgRir").toFloat(),
                            weeklyVolume = gObj.getDouble("weeklyVolume").toFloat(),
                        ),
                    )
                }
            }
        }

        macroStartCycleIds.forEach { (origMacroId, origStartCycleId) ->
            val newMacroId = macroIdMap[origMacroId] ?: return@forEach
            val mappedCycleId = cycleIdMap[origStartCycleId] ?: return@forEach
            val macro = dao.observeMacrocycles(userName).first()
                .find { it.id == newMacroId } ?: return@forEach
            dao.updateMacrocycle(
                macro.copy(startCycleId = mappedCycleId),
            )
        }
    }

    // ─── Duplicar cuenta ──────────────────────────────────────────────────────

    sealed class DuplicateResult {
        data class Success(val newUserName: String) : DuplicateResult()
        data class Error(val reason: String) : DuplicateResult()
    }

    fun suggestDuplicateName(existingNames: Collection<String>, sourceName: String): String {
        val existing = existingNames.map { it.lowercase() }.toSet()
        var candidate = "$sourceName (copia)"
        var index = 2
        while (candidate.lowercase() in existing) {
            candidate = "$sourceName (copia $index)"
            index++
        }
        return candidate
    }

    private suspend fun hasAccountData(dao: GymDao, userName: String): Boolean =
        dao.countSessionsForUser(userName) > 0 ||
            dao.countExercisesForUser(userName) > 0 ||
            dao.countMacrocyclesForUser(userName) > 0 ||
            dao.countForecastForUser(userName) > 0 ||
            dao.countTrainingCyclesForUser(userName) > 0 ||
            dao.observeAllFoodLogs(userName).first().isNotEmpty() ||
            dao.observeNutritionGoals(userName).first() != null ||
            dao.observeBodyMetrics(userName).first().isNotEmpty()

    suspend fun duplicateAccount(
        dao: GymDao,
        sourceUserName: String,
        targetUserName: String,
    ): DuplicateResult = withContext(Dispatchers.IO) {
        duplicateMutex.withLock {
            val trimmed = targetUserName.trim()
            when {
                trimmed.isBlank() -> return@withLock DuplicateResult.Error("Indica un nombre válido.")
                trimmed.equals(sourceUserName, ignoreCase = true) ->
                    return@withLock DuplicateResult.Error("La copia debe tener otro nombre.")
            }
            val existing = dao.observeProfiles().first().map { it.name }.toSet()
            if (existing.any { it.equals(trimmed, ignoreCase = true) }) {
                return@withLock DuplicateResult.Error("Ya existe una cuenta con ese nombre.")
            }
            if (hasAccountData(dao, trimmed)) {
                return@withLock DuplicateResult.Error("Ya existe una cuenta con ese nombre.")
            }

            try {
            val exerciseIdMap = mutableMapOf<Long, Long>()
            dao.observeExercises(sourceUserName).first().forEach { exercise ->
                val newId = dao.insertExercise(exercise.copy(id = 0L, userName = trimmed))
                exerciseIdMap[exercise.id] = newId
            }

            dao.observeSessions(sourceUserName).first().forEach { session ->
                val newSessionId = dao.insertSession(session.copy(id = 0L, userName = trimmed))
                dao.observeSetsBySession(session.id).first().forEach { set ->
                    val newExerciseId = exerciseIdMap[set.exerciseId] ?: return@forEach
                    dao.insertSet(
                        set.copy(
                            id = 0L,
                            sessionId = newSessionId,
                            exerciseId = newExerciseId,
                        ),
                    )
                }
            }

            exerciseIdMap.forEach { (oldExerciseId, newExerciseId) ->
                dao.observeForecastPoints(sourceUserName, oldExerciseId).first().forEach { point ->
                    dao.insertForecastPoints(
                        listOf(
                            point.copy(
                                userName = trimmed,
                                exerciseId = newExerciseId,
                            ),
                        ),
                    )
                }
            }

            val macroIdMap = mutableMapOf<Long, Long>()
            val cycleIdMap = mutableMapOf<Long, Long>()
            val sourceMacros = dao.observeMacrocycles(sourceUserName).first()
            sourceMacros.forEach { macro ->
                val newMacroId = dao.insertMacrocycle(
                    macro.copy(id = 0L, userName = trimmed),
                )
                macroIdMap[macro.id] = newMacroId
                dao.observeCyclesForMacrocycle(macro.id).first().forEach { cycle ->
                    val newCycleId = dao.insertTrainingCycle(
                        cycle.copy(id = 0L, macrocycleId = newMacroId, userName = trimmed),
                    )
                    cycleIdMap[cycle.id] = newCycleId
                    dao.observeCycleGroups(cycle.id).first().forEach { group ->
                        dao.upsertCycleGroup(
                            TrainingCycleGroupEntity(
                                cycleId = newCycleId,
                                muscleGroupKey = group.muscleGroupKey,
                                avgRir = group.avgRir,
                                weeklyVolume = group.weeklyVolume,
                            ),
                        )
                    }
                }
            }

            sourceMacros.forEach { macro ->
                if (macro.startCycleId <= 0L) return@forEach
                val newMacroId = macroIdMap[macro.id] ?: return@forEach
                val mappedCycleId = cycleIdMap[macro.startCycleId] ?: return@forEach
                dao.updateMacrocycle(
                    TrainingMacrocycleEntity(
                        id = newMacroId,
                        userName = trimmed,
                        name = macro.name,
                        createdAt = macro.createdAt,
                        isActive = macro.isActive,
                        activatedAt = macro.activatedAt,
                        startCycleId = mappedCycleId,
                        startWeekInCycle = macro.startWeekInCycle,
                        startDayInWeek = macro.startDayInWeek,
                    ),
                )
            }

            dao.insertProfile(ProfileEntity(name = trimmed))

            dao.observeAllFoodLogs(sourceUserName).first().forEach { log ->
                dao.insertFoodLog(log.copy(id = 0L, userName = trimmed))
            }
            dao.observeNutritionGoals(sourceUserName).first()?.let { goals ->
                dao.upsertNutritionGoals(goals.copy(userName = trimmed))
            }
            dao.observeBodyMetrics(sourceUserName).first().forEach { metric ->
                dao.upsertBodyMetric(metric.copy(userName = trimmed))
            }

            DuplicateResult.Success(trimmed)
            } catch (e: Exception) {
                Log.e(TAG, "Duplicate account failed", e)
                runCatching { dao.deleteAccountData(trimmed) }
                DuplicateResult.Error("No se pudo duplicar la cuenta.")
            }
        }
    }
}
