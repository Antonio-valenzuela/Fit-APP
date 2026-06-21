package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecommendationRulesTest {
    @Test
    fun `suggests soft session when sleep is low`() {
        val recommendations = LocalRecommendationRules.build(
            discipline = UserDiscipline.GYM,
            goal = "Bajar grasa",
            workouts = emptyList(),
            activitySessions = emptyList(),
            todayHealthLogs = listOf(HealthLogEntity(type = "sleep", sleepDurationHours = 4.5f)),
            todayFoodLogs = emptyList(),
            muscleSuggestion = null
        )

        assertEquals("Sesion suave", recommendations.first().title)
        assertTrue(recommendations.first().reason.contains("poco sueno"))
    }

    @Test
    fun `uses discipline specific primary recommendation`() {
        val recommendations = LocalRecommendationRules.build(
            discipline = UserDiscipline.RUNNING,
            goal = "Correr mas",
            workouts = emptyList(),
            activitySessions = emptyList(),
            todayHealthLogs = emptyList(),
            todayFoodLogs = emptyList(),
            muscleSuggestion = null
        )

        assertEquals("Carrera suave", recommendations.first().title)
    }
}
