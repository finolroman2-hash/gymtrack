package com.example.gymtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.ForecastPointEntity
import com.example.gymtrack.data.local.entities.ProfileEntity
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SessionSetPreviewRow
import com.example.gymtrack.data.local.entities.SetEntity
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.data.local.entities.NutritionGoalsEntity
import com.example.gymtrack.data.local.entities.BodyMetricEntity
import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.data.local.entities.SetStatRow
import com.example.gymtrack.data.local.entities.SetProgressRow
import com.example.gymtrack.data.local.entities.SetProgressWithSession
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {

    // ── Perfiles ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("UPDATE profiles SET name = :newName WHERE name = :oldName")
    suspend fun renameProfile(oldName: String, newName: String)

    @Query("UPDATE sessions SET userName = :newName WHERE userName = :oldName")
    suspend fun renameSessionsUser(oldName: String, newName: String)

    @Query("UPDATE exercises SET userName = :newName WHERE userName = :oldName")
    suspend fun renameExercisesUser(oldName: String, newName: String)

    @Query("DELETE FROM sessions WHERE userName = :userName")
    suspend fun deleteSessionsByUser(userName: String)

    @Query("DELETE FROM exercises WHERE userName = :userName")
    suspend fun deleteExercisesByUser(userName: String)

    @Query("DELETE FROM forecast_points WHERE userName = :userName")
    suspend fun deleteForecastPointsByUser(userName: String)

    @Query("DELETE FROM training_macrocycles WHERE userName = :userName")
    suspend fun deleteMacrocyclesByUser(userName: String)

    @Query("DELETE FROM training_cycles WHERE userName = :userName")
    suspend fun deleteTrainingCyclesByUser(userName: String)

    @Query("UPDATE training_macrocycles SET userName = :newName WHERE userName = :oldName")
    suspend fun renameMacrocyclesUser(oldName: String, newName: String)

    @Query("UPDATE training_cycles SET userName = :newName WHERE userName = :oldName")
    suspend fun renameTrainingCyclesUser(oldName: String, newName: String)

    @Query("UPDATE forecast_points SET userName = :newName WHERE userName = :oldName")
    suspend fun renameForecastPointsUser(oldName: String, newName: String)

    /** Borra perfil y todos los datos asociados (evita que ProfileRecovery lo resucite). */
    @Transaction
    suspend fun deleteAccountData(userName: String) {
        deleteSessionsByUser(userName)
        deleteExercisesByUser(userName)
        deleteForecastPointsByUser(userName)
        deleteTrainingCyclesByUser(userName)
        deleteMacrocyclesByUser(userName)
        deleteFoodLogsByUser(userName)
        deleteNutritionGoalsByUser(userName)
        deleteBodyMetricsByUser(userName)
        deleteProfile(ProfileEntity(name = userName))
    }

    /** Elimina datos huérfanos sin perfil (p. ej. restos tras borrado incompleto). */
    @Transaction
    suspend fun purgeOrphanUserData(userName: String) {
        deleteSessionsByUser(userName)
        deleteExercisesByUser(userName)
        deleteForecastPointsByUser(userName)
        deleteTrainingCyclesByUser(userName)
        deleteMacrocyclesByUser(userName)
        deleteFoodLogsByUser(userName)
        deleteNutritionGoalsByUser(userName)
        deleteBodyMetricsByUser(userName)
    }

    /** Renombra cuenta en todas las tablas con userName. */
    @Transaction
    suspend fun renameAccountUser(oldName: String, newName: String) {
        renameProfile(oldName, newName)
        renameSessionsUser(oldName, newName)
        renameExercisesUser(oldName, newName)
        renameMacrocyclesUser(oldName, newName)
        renameTrainingCyclesUser(oldName, newName)
        renameForecastPointsUser(oldName, newName)
        renameFoodLogsUser(oldName, newName)
        renameNutritionGoalsUser(oldName, newName)
        renameBodyMetricsUser(oldName, newName)
    }

    @Query(
        """
        SELECT DISTINCT userName FROM (
            SELECT userName FROM sessions
            UNION SELECT userName FROM exercises
            UNION SELECT userName FROM training_macrocycles
            UNION SELECT userName FROM training_cycles
            UNION SELECT userName FROM forecast_points
            UNION SELECT userName FROM food_logs
            UNION SELECT userName FROM nutrition_goals
            UNION SELECT userName FROM body_metrics
        )
        WHERE userName IS NOT NULL AND userName != ''
        ORDER BY userName COLLATE NOCASE ASC
        """,
    )
    suspend fun listAllKnownUserNames(): List<String>

    @Query("SELECT COUNT(*) FROM sessions WHERE userName = :userName")
    suspend fun countSessionsForUser(userName: String): Int

    @Query("SELECT COUNT(*) FROM exercises WHERE userName = :userName")
    suspend fun countExercisesForUser(userName: String): Int

    @Query("SELECT COUNT(*) FROM training_macrocycles WHERE userName = :userName")
    suspend fun countMacrocyclesForUser(userName: String): Int

    @Query("SELECT COUNT(*) FROM forecast_points WHERE userName = :userName")
    suspend fun countForecastForUser(userName: String): Int

    @Query("SELECT COUNT(*) FROM training_cycles WHERE userName = :userName")
    suspend fun countTrainingCyclesForUser(userName: String): Int

    // ── Sesiones ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Update
    suspend fun updateSet(set: SetEntity)

    @Delete
    suspend fun deleteSet(set: SetEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE userName = :userName ORDER BY dateTimestamp DESC")
    fun observeSessions(userName: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM exercises WHERE userName = :userName ORDER BY name ASC")
    fun observeExercises(userName: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    fun observeSetsBySession(sessionId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    suspend fun fetchSetsOrderedForSession(sessionId: Long): List<SetEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM sets WHERE sessionId = :sessionId")
    suspend fun nextSortOrderForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM sets WHERE sessionId = :sessionId")
    suspend fun countSetsInSession(sessionId: Long): Int

    @Query("SELECT DISTINCT exerciseId FROM sets WHERE sessionId = :sessionId")
    fun observeExerciseIdsInSession(sessionId: Long): Flow<List<Long>>

    @Query("SELECT DISTINCT exerciseId FROM sets WHERE sessionId IN (:sessionIds)")
    fun observeExerciseIdsInSessions(sessionIds: List<Long>): Flow<List<Long>>

    @Query("""
        SELECT s.exerciseId AS exerciseId, s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE sess.userName = :userName
    """)
    fun observeSetStatsForUser(userName: String): Flow<List<SetStatRow>>
    @Query("SELECT DISTINCT userName FROM sessions ORDER BY userName ASC")
    suspend fun getAllUserNames(): List<String>

    @Query("""
        SELECT sess.dateTimestamp AS sessionDate, s.reps AS reps, s.weight AS weight, ex.unitType AS unitType
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId
        AND sess.userName = :userName
        AND sess.dateTimestamp BETWEEN :fromTs AND :toTs
        ORDER BY sess.dateTimestamp ASC
    """)
    fun observeProgress(
        userName: String,
        exerciseId: Long,
        fromTs: Long,
        toTs: Long
    ): Flow<List<SetProgressRow>>

    @Query("""
        SELECT sess.dateTimestamp AS sessionDate, s.reps AS reps, s.weight AS weight, ex.unitType AS unitType
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId
        AND sess.userName = :userName
        ORDER BY sess.dateTimestamp ASC
    """)
    fun observeProgressAll(userName: String, exerciseId: Long): Flow<List<SetProgressRow>>

    // Progress with session info (includes sessionId and rir for 1RM + navigation)
    @Query("""
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId
        AND sess.userName = :userName
        ORDER BY sess.dateTimestamp ASC, s.sortOrder ASC, s.createdAt ASC, s.id ASC
    """)
    fun observeProgressAllWithSession(userName: String, exerciseId: Long): Flow<List<SetProgressWithSession>>

    /** Histórico combinado de varios ejercicios (p. ej. mismo grupo muscular). */
    @Query(
        """
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE sess.userName = :userName AND s.exerciseId IN (:exerciseIds)
        ORDER BY sess.dateTimestamp ASC, s.sortOrder ASC, s.createdAt ASC, s.id ASC
        """,
    )
    fun observeProgressForExerciseIds(userName: String, exerciseIds: List<Long>): Flow<List<SetProgressWithSession>>

    @Query(
        """
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE sess.id = :sessionId AND s.exerciseId IN (:exerciseIds)
        ORDER BY sess.dateTimestamp ASC, s.sortOrder ASC, s.createdAt ASC, s.id ASC
        """,
    )
    fun observeProgressForExerciseIdsInSession(sessionId: Long, exerciseIds: List<Long>): Flow<List<SetProgressWithSession>>

    @Query(
        """
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE sess.id IN (:sessionIds) AND s.exerciseId IN (:exerciseIds)
        ORDER BY sess.dateTimestamp ASC, s.sortOrder ASC, s.createdAt ASC, s.id ASC
        """,
    )
    fun observeProgressForExerciseIdsInSessions(
        sessionIds: List<Long>,
        exerciseIds: List<Long>,
    ): Flow<List<SetProgressWithSession>>

    // Progress filtered to a specific session (for session-stats mode)
    @Query("""
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId
        AND sess.id = :sessionId
        ORDER BY s.sortOrder ASC, s.createdAt ASC, s.id ASC
    """)
    fun observeProgressBySession(exerciseId: Long, sessionId: Long): Flow<List<SetProgressWithSession>>

    @Query("""
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId
        AND sess.id IN (:sessionIds)
        ORDER BY sess.dateTimestamp ASC, s.sortOrder ASC, s.createdAt ASC, s.id ASC
    """)
    fun observeProgressBySessions(
        exerciseId: Long,
        sessionIds: List<Long>,
    ): Flow<List<SetProgressWithSession>>

    // All sets of a session joined with exercise name and unitType (for session stats overview)
    @Query("""
        SELECT s.id AS setId, sess.id AS sessionId, s.exerciseId AS exerciseId,
               sess.dateTimestamp AS sessionDate, sess.customName AS sessionName,
               ex.name AS exerciseName,
               s.note AS note,
               s.reps AS reps, s.weight AS weight, s.rir AS rir, ex.unitType AS unitType,
               s.sortOrder + 1 AS setOrder
        FROM sets s
        INNER JOIN sessions sess ON sess.id = s.sessionId
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE sess.id = :sessionId
        ORDER BY s.sortOrder ASC, s.createdAt ASC, s.id ASC
    """)
    fun observeAllProgressBySession(sessionId: Long): Flow<List<SetProgressWithSession>>

    /** Vista rápida de una sesión: orden real, sin recorte. */
    @Query("""
        SELECT ex.name AS exerciseName, s.reps AS reps, s.weight AS weight, s.rir AS rir
        FROM sets s
        INNER JOIN exercises ex ON ex.id = s.exerciseId
        WHERE s.sessionId = :sessionId
        ORDER BY s.sortOrder ASC, s.createdAt ASC, s.id ASC
    """)
    fun observeSessionPreview(sessionId: Long): Flow<List<SessionSetPreviewRow>>

    // ── Pronosticaciones ───────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM forecast_points
        WHERE userName = :userName AND exerciseId = :exerciseId
        ORDER BY dateMillis ASC
        """,
    )
    fun observeForecastPoints(userName: String, exerciseId: Long): Flow<List<ForecastPointEntity>>

    @Query(
        """
        SELECT * FROM forecast_points
        WHERE userName = :userName AND exerciseId = :exerciseId
        AND dateMillis >= :startMillis AND dateMillis <= :endMillis
        ORDER BY dateMillis ASC
        """,
    )
    suspend fun getForecastPointsInRange(
        userName: String,
        exerciseId: Long,
        startMillis: Long,
        endMillis: Long,
    ): List<ForecastPointEntity>

    @Query(
        """
        DELETE FROM forecast_points
        WHERE userName = :userName AND exerciseId = :exerciseId
        AND dateMillis >= :startMillis AND dateMillis <= :endMillis
        """,
    )
    suspend fun deleteForecastPointsInRange(
        userName: String,
        exerciseId: Long,
        startMillis: Long,
        endMillis: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecastPoints(points: List<ForecastPointEntity>)

    // ── Programación de entrenamiento ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacrocycle(entity: TrainingMacrocycleEntity): Long

    @Update
    suspend fun updateMacrocycle(entity: TrainingMacrocycleEntity)

    @Delete
    suspend fun deleteMacrocycle(entity: TrainingMacrocycleEntity)

    @Query("SELECT * FROM training_macrocycles WHERE userName = :userName ORDER BY createdAt ASC")
    fun observeMacrocycles(userName: String): Flow<List<TrainingMacrocycleEntity>>

    @Query(
        """
        SELECT * FROM training_macrocycles
        WHERE userName = :userName AND isActive = 1
        LIMIT 1
        """,
    )
    fun observeActiveMacrocycle(userName: String): Flow<TrainingMacrocycleEntity?>

    @Query(
        """
        UPDATE training_macrocycles
        SET isActive = 0, activatedAt = 0, startCycleId = 0, startWeekInCycle = 1, startDayInWeek = 1
        WHERE userName = :userName
        """,
    )
    suspend fun deactivateAllMacrocycles(userName: String)

    @Query(
        """
        UPDATE training_macrocycles
        SET isActive = 1, activatedAt = :activatedAt,
            startCycleId = :startCycleId, startWeekInCycle = :startWeekInCycle, startDayInWeek = :startDayInWeek
        WHERE id = :macroId AND userName = :userName
        """,
    )
    suspend fun activateMacrocycle(
        macroId: Long,
        userName: String,
        activatedAt: Long,
        startCycleId: Long,
        startWeekInCycle: Int,
        startDayInWeek: Int,
    )

    @Transaction
    suspend fun setActiveMacrocycle(
        userName: String,
        macroId: Long?,
        activatedAt: Long,
        startCycleId: Long = 0L,
        startWeekInCycle: Int = 1,
        startDayInWeek: Int = 1,
    ) {
        deactivateAllMacrocycles(userName)
        if (macroId != null) {
            activateMacrocycle(
                macroId, userName, activatedAt, startCycleId, startWeekInCycle, startDayInWeek,
            )
        }
    }

    @Query("SELECT * FROM training_macrocycles WHERE id = :macroId LIMIT 1")
    suspend fun getMacrocycleById(macroId: Long): TrainingMacrocycleEntity?

    @Query(
        """
        SELECT sets.* FROM sets
        INNER JOIN sessions ON sets.sessionId = sessions.id
        WHERE sessions.userName = :userName
        AND sessions.dateTimestamp >= :weekStart
        AND sessions.dateTimestamp < :weekEnd
        """,
    )
    fun observeSetsForUserInWeek(
        userName: String,
        weekStart: Long,
        weekEnd: Long,
    ): Flow<List<SetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingCycle(entity: TrainingCycleEntity): Long

    @Update
    suspend fun updateTrainingCycle(entity: TrainingCycleEntity)

    @Delete
    suspend fun deleteTrainingCycle(entity: TrainingCycleEntity)

    @Query(
        """
        SELECT * FROM training_cycles
        WHERE macrocycleId = :macrocycleId
        ORDER BY sortOrder ASC
        """,
    )
    fun observeCyclesForMacrocycle(macrocycleId: Long): Flow<List<TrainingCycleEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM training_cycles WHERE macrocycleId = :macrocycleId")
    suspend fun maxCycleSortOrder(macrocycleId: Long): Int

    @Query(
        """
        SELECT * FROM training_cycle_groups
        WHERE cycleId = :cycleId
        ORDER BY muscleGroupKey ASC
        """,
    )
    fun observeCycleGroups(cycleId: Long): Flow<List<TrainingCycleGroupEntity>>

    @Query(
        """
        SELECT g.* FROM training_cycle_groups g
        INNER JOIN training_cycles c ON g.cycleId = c.id
        WHERE c.macrocycleId = :macrocycleId
        ORDER BY c.sortOrder ASC, g.muscleGroupKey ASC
        """,
    )
    fun observeCycleGroupsForMacrocycle(macrocycleId: Long): Flow<List<TrainingCycleGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycleGroup(entity: TrainingCycleGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycleGroups(entities: List<TrainingCycleGroupEntity>)

    @Delete
    suspend fun deleteCycleGroup(entity: TrainingCycleGroupEntity)

    @Query("DELETE FROM training_cycle_groups WHERE cycleId = :cycleId AND muscleGroupKey = :muscleGroupKey")
    suspend fun deleteCycleGroupByKey(cycleId: Long, muscleGroupKey: String)

    // ── Nutrición / Meal Track ───────────────────────────────────────────────

    @Insert
    suspend fun insertFoodLog(entity: FoodLogEntity): Long

    @Update
    suspend fun updateFoodLog(entity: FoodLogEntity)

    @Delete
    suspend fun deleteFoodLog(entity: FoodLogEntity)

    @Query("SELECT * FROM food_logs WHERE userName = :userName AND dayStartMillis = :dayStart ORDER BY createdAt DESC")
    fun observeFoodLogsForDay(userName: String, dayStart: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs WHERE userName = :userName AND dayStartMillis >= :from AND dayStartMillis < :to ORDER BY dayStartMillis ASC")
    fun observeFoodLogsInRange(userName: String, from: Long, to: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs WHERE userName = :userName ORDER BY dayStartMillis ASC, createdAt ASC")
    fun observeAllFoodLogs(userName: String): Flow<List<FoodLogEntity>>

    @Query("DELETE FROM food_logs WHERE userName = :userName")
    suspend fun deleteFoodLogsByUser(userName: String)

    @Query("UPDATE food_logs SET userName = :newName WHERE userName = :oldName")
    suspend fun renameFoodLogsUser(oldName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNutritionGoals(entity: NutritionGoalsEntity)

    @Query("SELECT * FROM nutrition_goals WHERE userName = :userName LIMIT 1")
    fun observeNutritionGoals(userName: String): Flow<NutritionGoalsEntity?>

    @Query("DELETE FROM nutrition_goals WHERE userName = :userName")
    suspend fun deleteNutritionGoalsByUser(userName: String)

    @Query("UPDATE nutrition_goals SET userName = :newName WHERE userName = :oldName")
    suspend fun renameNutritionGoalsUser(oldName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBodyMetric(entity: BodyMetricEntity)

    @Query("SELECT * FROM body_metrics WHERE userName = :userName ORDER BY dayStartMillis ASC")
    fun observeBodyMetrics(userName: String): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics WHERE userName = :userName AND dayStartMillis = :dayStart LIMIT 1")
    suspend fun bodyMetricForDay(userName: String, dayStart: Long): BodyMetricEntity?

    @Query("DELETE FROM body_metrics WHERE userName = :userName")
    suspend fun deleteBodyMetricsByUser(userName: String)

    @Query("UPDATE body_metrics SET userName = :newName WHERE userName = :oldName")
    suspend fun renameBodyMetricsUser(oldName: String, newName: String)
}
