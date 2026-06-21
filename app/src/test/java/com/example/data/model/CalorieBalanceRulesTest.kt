package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieBalanceRulesTest {
    @Test
    fun `uses only real logged calories for estimated balance`() {
        val meals = listOf(
            FoodLogEntity(mealName = "huevo", mealType = "DESAYUNO", calories = 220, proteinG = 18f, carbsG = 2f, fatG = 14f),
            FoodLogEntity(mealName = "comida sin kcal", mealType = "COMIDA", calories = 0, proteinG = 0f, carbsG = 0f, fatG = 0f)
        )
        val workouts = listOf(
            WorkoutLogEntity(routineName = "Full body", durationMinutes = 30, caloriesBurned = 180, exercisesText = "")
        )
        val sessions = listOf(
            ActivitySessionEntity(discipline = "RUNNING", title = "Carrera", caloriesBurned = 120)
        )

        val summary = CalorieBalanceRules.build(
            legacyMeals = meals,
            workouts = workouts,
            sessions = sessions,
            healthLogs = emptyList()
        )

        assertEquals(220, summary.consumedCalories)
        assertEquals(300, summary.burnedCalories)
        assertEquals(-80, summary.balanceCalories)
        assertEquals("Balance estimado", summary.title)
    }

    @Test
    fun `includes water from health logs`() {
        val health = listOf(
            HealthLogEntity(waterIntakeMl = 250),
            HealthLogEntity(waterIntakeMl = 600)
        )

        val summary = CalorieBalanceRules.build(
            legacyMeals = emptyList(),
            workouts = emptyList(),
            sessions = emptyList(),
            healthLogs = health
        )

        assertEquals(850, summary.waterMl)
    }
}
