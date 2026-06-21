package com.example.data.model

data class CalorieBalanceSummary(
    val title: String = "Balance estimado",
    val consumedCalories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val waterMl: Int,
    val burnedCalories: Int,
    val balanceCalories: Int,
    val hasBurnData: Boolean
)

object CalorieBalanceRules {
    const val disclaimer = "Este calculo es informativo y puede variar segun tu metabolismo, intensidad y registros."

    fun build(
        legacyMeals: List<FoodLogEntity>,
        entries: List<FoodEntryEntity> = emptyList(),
        workouts: List<WorkoutLogEntity>,
        sessions: List<ActivitySessionEntity>,
        healthLogs: List<HealthLogEntity>
    ): CalorieBalanceSummary {
        val consumed = legacyMeals.sumOf { it.calories.coerceAtLeast(0) } + entries.sumOf { it.calories.coerceAtLeast(0) }
        val burned = workouts.sumOf { it.caloriesBurned.coerceAtLeast(0) } +
            sessions.sumOf { it.caloriesBurned.coerceAtLeast(0) }
        return CalorieBalanceSummary(
            consumedCalories = consumed,
            proteinGrams = legacyMeals.sumOf { it.proteinG.toDouble() }.toFloat() + entries.sumOf { it.proteinGrams.toDouble() }.toFloat(),
            carbsGrams = legacyMeals.sumOf { it.carbsG.toDouble() }.toFloat() + entries.sumOf { it.carbsGrams.toDouble() }.toFloat(),
            fatGrams = legacyMeals.sumOf { it.fatG.toDouble() }.toFloat() + entries.sumOf { it.fatGrams.toDouble() }.toFloat(),
            waterMl = healthLogs.sumOf { it.waterIntakeMl.coerceAtLeast(0) },
            burnedCalories = burned,
            balanceCalories = consumed - burned,
            hasBurnData = burned > 0
        )
    }
}
