package com.example.gymtrack.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_logs",
    indices = [Index("userName"), Index("userName", "dayStartMillis")],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userName: String,
    val dayStartMillis: Long,
    val offCode: String = "",
    val name: String,
    val brand: String = "",
    val quantity: Float,
    val unit: String,
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "nutrition_goals")
data class NutritionGoalsEntity(
    @PrimaryKey val userName: String,
    val sex: String,
    val age: Int,
    val heightCm: Float,
    val weightKg: Float,
    val activity: String,
    val goal: String,
    val tdee: Int,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "body_metrics",
    primaryKeys = ["userName", "dayStartMillis"],
)
data class BodyMetricEntity(
    val userName: String,
    val dayStartMillis: Long,
    val weightKg: Float,
    val bodyFatPercent: Float,
)
