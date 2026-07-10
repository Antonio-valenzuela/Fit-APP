package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String = "local_user",
    val name: String = "",
    val email: String = "",
    val age: Int = 25,
    val weightKg: Float = 70.0f,
    val heightCm: Float = 170.0f,
    val gender: String = "Masculino",
    val activityLevel: String = "Moderado", // Sedentario, Ligero, Moderado, Activo
    val goal: String = "Mantener Peso", // Perder Peso, Mantener Peso, Ganar Músculo
    val targetCalories: Int = 2000,
    val targetProteinG: Int = 140,
    val targetCarbsG: Int = 220,
    val targetFatG: Int = 65,
    val disclaimerAccepted: Boolean = false,
    val isPremium: Boolean = false,
    @ColumnInfo(defaultValue = "GYM")
    val trainingDiscipline: String = UserDiscipline.GYM.name
)

fun UserEntity.getTrainingPlace(): String {
    val parts = goal.split("|")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.startsWith("Lugar:", ignoreCase = true)) {
            return trimmed.substringAfter("Lugar:").trim()
        }
    }
    return "Ambos"
}


@Entity(
    tableName = "food_logs",
    indices = [Index("timestamp")]
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mealName: String,
    val mealType: String, // Desayuno, Comida, Cena, Snack
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "food_entries",
    indices = [Index("dateKey"), Index("mealType"), Index("barcode")]
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    val mealType: String,
    val foodName: String,
    val portionLabel: String,
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val saturatedFatGrams: Float? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val sodiumMg: Float? = null,
    val source: String = "MANUAL",
    val barcode: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "food_items",
    indices = [Index(value = ["barcode"], unique = true), Index("isFavorite"), Index("name")]
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val caloriesPerServing: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val saturatedFatGrams: Float? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val sodiumMg: Float? = null,
    val servingLabel: String = "Plato normal",
    val isFavorite: Boolean = false,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workout_logs",
    indices = [Index("timestamp")]
)
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routineName: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val exercisesText: String, // Lista separada por comas de ejercicios realizados
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "activity_sessions",
    indices = [Index("discipline"), Index("timestamp")]
)
data class ActivitySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val discipline: String,
    @ColumnInfo(defaultValue = "")
    val sportType: String = discipline,
    val title: String,
    val distanceKm: Float = 0.0f,
    val durationMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    @ColumnInfo(defaultValue = "")
    val routePointsJson: String = "",
    @ColumnInfo(defaultValue = "0")
    val startedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val endedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val durationSeconds: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val averagePaceMinPerKm: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val averageSpeedKmh: Double = 0.0,
    @ColumnInfo(defaultValue = "local_manual")
    val source: String = "local_manual",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "health_logs",
    indices = [Index("timestamp"), Index("dateKey")]
)
data class HealthLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(defaultValue = "")
    val type: String = "",
    @ColumnInfo(defaultValue = "0")
    val value: Float = 0.0f,
    @ColumnInfo(defaultValue = "0")
    val secondaryValue: Float = 0.0f,
    @ColumnInfo(defaultValue = "")
    val unit: String = "",
    @ColumnInfo(defaultValue = "")
    val context: String = "",
    @ColumnInfo(defaultValue = "")
    val note: String = "",
    @ColumnInfo(defaultValue = "")
    val dateKey: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val pulse: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val isAutoWorkout: Boolean = false,
    val glucoseLevel: Float = 0.0f, // mg/dL (0 = no registrado)
    val glucoseIsPreMeal: Boolean = true,
    val systolicPressure: Int = 0, // mmHg (0 = no registrado)
    val diastolicPressure: Int = 0, // mmHg (0 = no registrado)
    val waterIntakeMl: Int = 0, // mL
    val sleepDurationHours: Float = 0.0f, // Horas
    val sleepQualityScale: Int = 0, // 0-10
    @ColumnInfo(defaultValue = "0")
    val energyLevel: Int = 0, // 0-10
    @ColumnInfo(defaultValue = "0")
    val moodLevel: Int = 0, // 0-10
    @ColumnInfo(defaultValue = "0")
    val painFatigueLevel: Int = 0, // 0-10
    @ColumnInfo(defaultValue = "")
    val painFatigueNote: String = "",
    @ColumnInfo(defaultValue = "0")
    val steps: Int = 0,
    val weightKg: Float = 0.0f, // Peso corporal registrado
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_activity",
    indices = [Index("userId", "date")]
)
data class DailyActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val date: String,
    val hasFoodLog: Boolean = false,
    val hasWaterLog: Boolean = false,
    val hasWorkoutLog: Boolean = false,
    val hasWeightLog: Boolean = false,
    val hasSleepLog: Boolean = false,
    val hasCheckIn: Boolean = false,
    val hasMissionCompleted: Boolean = false,
    val isActiveDay: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey
    val userId: String,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDate: String? = null,
    val updatedAt: Long
)

@Entity(
    tableName = "daily_missions",
    indices = [Index("userId", "date"), Index("userId", "date", "missionKey")]
)
data class DailyMissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val date: String,
    val missionKey: String,
    val title: String,
    val description: String,
    val completed: Boolean = false,
    val completedAt: Long? = null
)

@Entity(
    tableName = "achievements",
    indices = [Index("userId"), Index("userId", "achievementKey")]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val achievementKey: String,
    val title: String,
    val description: String,
    val unlockedAt: Long
)

