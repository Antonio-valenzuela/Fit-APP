package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTodaySummaryRulesTest {
    @Test
    fun `builds practical plan from local recommendation`() {
        val summary = DashboardTodaySummaryRules.build(
            recommendation = LocalRecommendation(
                title = "Rutina corta",
                reason = "No vemos entrenamiento reciente.",
                actionLabel = "Empezar",
                routeHint = "workout"
            ),
            todayHealthLogs = emptyList(),
            todayFoodLogs = emptyList(),
            workouts = emptyList(),
            activitySessions = emptyList(),
            recommendedMuscle = null,
            discipline = UserDiscipline.GYM,
            nowMillis = 1_700_000_000_000L
        )

        assertEquals("Rutina corta", summary.planTitle)
        assertEquals("Empezar", summary.primaryActionLabel)
        assertEquals("workout", summary.primaryRouteHint)
        assertEquals("55/100 Base inicial", summary.readinessLabel)
        assertEquals("Necesitamos más registros para darte una recomendación más precisa.", summary.readinessMessage)
    }

    @Test
    fun `quick status uses human language for missing food and low water`() {
        val summary = DashboardTodaySummaryRules.build(
            recommendation = LocalRecommendation(
                title = "Plan simple",
                reason = "Mantente activo.",
                actionLabel = "Entrenar",
                routeHint = "workout"
            ),
            todayHealthLogs = listOf(HealthLogEntity(waterIntakeMl = 600)),
            todayFoodLogs = emptyList(),
            allHealthLogs = listOf(HealthLogEntity(weightKg = 77f)),
            workouts = emptyList(),
            activitySessions = emptyList(),
            recommendedMuscle = Muscle.SHOULDERS,
            discipline = UserDiscipline.GYM,
            nowMillis = 1_700_000_000_000L
        )

        assertEquals("Hidratacion baja", summary.waterStatus)
        assertEquals("Falta registrar comida", summary.foodStatus)
        assertEquals("Pendiente: Hombros", summary.muscleStatus)
        assertEquals("Sin entrenamiento reciente", summary.lastTrainingStatus)
        assertTrue(summary.readinessLabel.contains("/100"))
        assertTrue(summary.planMessage.contains("hidrátate") || summary.planMessage.contains("moderada"))
    }

    @Test
    fun `quick status identifies a recent workout`() {
        val now = 1_700_000_000_000L
        val summary = DashboardTodaySummaryRules.build(
            recommendation = LocalRecommendation(
                title = "Plan simple",
                reason = "Mantente activo.",
                actionLabel = "Entrenar",
                routeHint = "workout"
            ),
            todayHealthLogs = listOf(HealthLogEntity(waterIntakeMl = 1800)),
            todayFoodLogs = listOf(
                FoodLogEntity(
                    mealName = "Avena",
                    mealType = "Desayuno",
                    calories = 350,
                    proteinG = 20f,
                    carbsG = 45f,
                    fatG = 8f
                )
            ),
            allHealthLogs = listOf(HealthLogEntity(weightKg = 80f, sleepDurationHours = 7.2f, energyLevel = 8)),
            workouts = listOf(
                WorkoutLogEntity(
                    routineName = "Fuerza",
                    durationMinutes = 45,
                    caloriesBurned = 250,
                    exercisesText = "sentadilla",
                    timestamp = now - (3 * 60 * 60 * 1000L)
                )
            ),
            activitySessions = emptyList(),
            recommendedMuscle = null,
            discipline = UserDiscipline.GYM,
            nowMillis = now
        )

        assertEquals("Agua registrada: 1800 ml", summary.waterStatus)
        assertEquals("1 comida registrada", summary.foodStatus)
        assertEquals("Entrenaste hoy", summary.lastTrainingStatus)
    }
}
