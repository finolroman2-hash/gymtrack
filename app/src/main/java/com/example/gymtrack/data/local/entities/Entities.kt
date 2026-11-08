package com.example.gymtrack.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UnitType { KG, PLATES }

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userName: String,
    val name: String,
    val unitType: UnitType,
    /** Comas separando etiquetas de [com.example.gymtrack.domain.ExerciseMuscleTags.ALL]. */
    val muscleTags: String = "",
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userName: String,
    val customName: String,
    val dateTimestamp: Long
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    val rir: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Orden manual dentro de la sesión (menor = más arriba). */
    val sortOrder: Int = 0,
)

data class SetProgressRow(
    val sessionDate: Long,
    val reps: Int,
    val weight: Float,
    val unitType: UnitType
)

data class SessionSetPreviewRow(
    val exerciseName: String,
    val reps: Int,
    val weight: Float,
    val rir: Int
)

data class SetProgressWithSession(
    val setId: Long = 0L,
    val sessionId: Long,
    val exerciseId: Long = 0L,
    val sessionDate: Long,
    val sessionName: String,
    val exerciseName: String = "",
    val note: String = "",
    val reps: Int,
    val weight: Float,
    val rir: Int,
    val unitType: UnitType,
    /** Posición 1-based de la serie dentro de la sesión. */
    val setOrder: Int = 0,
)

/** Punto diario de una pronosticación de 1RM para un ejercicio. */
@Entity(
    tableName = "forecast_points",
    primaryKeys = ["userName", "exerciseId", "dateMillis"],
    indices = [Index("exerciseId"), Index(value = ["userName", "exerciseId"])],
)
data class ForecastPointEntity(
    val userName: String,
    val exerciseId: Long,
    /** Inicio del día local (epoch ms). */
    val dateMillis: Long,
    val predictedOneRm: Float,
)

@Entity(tableName = "training_macrocycles")
data class TrainingMacrocycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userName: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Solo un macrociclo activo por usuario; guía volumen/RIR en sesiones. */
    val isActive: Boolean = false,
    /** Momento en que se confirmó la activación (wizard). */
    val activatedAt: Long = 0L,
    /** Ciclo elegido al activar (no altera el orden). */
    val startCycleId: Long = 0L,
    /** Semana dentro del ciclo elegido (1-based). */
    val startWeekInCycle: Int = 1,
    /** Día dentro de la semana elegida (1–7). */
    val startDayInWeek: Int = 1,
)

@Entity(
    tableName = "training_cycles",
    foreignKeys = [
        ForeignKey(
            entity = TrainingMacrocycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["macrocycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macrocycleId"), Index("userName")],
)
data class TrainingCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val macrocycleId: Long,
    val userName: String,
    /** Orden cronológico dentro del macrociclo (0 = primero). */
    val sortOrder: Int,
    /** Duración del ciclo en semanas (1–48). */
    val weeks: Int,
    /** RIR promedio objetivo durante el ciclo. */
    val avgRir: Float,
    /** Volumen semanal objetivo (series totales por semana activa). */
    val weeklyVolume: Float,
    val label: String = "",
)

@Entity(
    tableName = "training_cycle_groups",
    primaryKeys = ["cycleId", "muscleGroupKey"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycleId")],
)
data class TrainingCycleGroupEntity(
    val cycleId: Long,
    val muscleGroupKey: String,
    val avgRir: Float,
    val weeklyVolume: Float,
)

/** Fila mínima para agregar estadísticas por ejercicio (histórico del usuario). */
data class SetStatRow(
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    val rir: Int,
    val unitType: UnitType,
)
