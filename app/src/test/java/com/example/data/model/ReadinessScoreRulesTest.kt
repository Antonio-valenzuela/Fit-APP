package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessScoreRulesTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `returns human low confidence message when data is missing`() {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = emptyList(),
            todayFoodLogs = emptyList(),
            allHealthLogs = emptyList(),
            workouts = emptyList(),
            activitySessions = emptyList(),
            recommendedMuscle = null,
            discipline = UserDiscipline.GYM,
            nowMillis = now
        )

        val readiness = ReadinessScoreRules.calculate(monitor)

        assertEquals(55, readiness.score)
        assertEquals("55/100 Base inicial", readiness.label)
        assertEquals("Necesitamos más registros para darte una recomendación más precisa.", readiness.message)
        assertTrue(readiness.reasons.any { it.contains("registros") })
    }

    @Test
    fun `lowers score for low hydration fatigue low sleep and overtraining`() {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = listOf(
                HealthLogEntity(
                    waterIntakeMl = 500,
                    sleepDurationHours = 5.0f,
                    energyLevel = 3,
                    painFatigueLevel = 8,
                    timestamp = now
                )
            ),
            todayFoodLogs = listOf(food("Avena", "Desayuno", 350, now)),
            allHealthLogs = listOf(HealthLogEntity(weightKg = 76f, timestamp = now - day)),
            workouts = listOf(
                workout(now - day),
                workout(now - (2 * day)),
                workout(now - (3 * day)),
                workout(now - (4 * day))
            ),
            activitySessions = listOf(
                ActivitySessionEntity(
                    discipline = UserDiscipline.RUNNING.name,
                    title = "Carrera",
                    durationMinutes = 35,
                    timestamp = now - (2 * day)
                )
            ),
            recommendedMuscle = Muscle.CHEST,
            discipline = UserDiscipline.HYBRID,
            nowMillis = now
        )

        val readiness = ReadinessScoreRules.calculate(monitor)

        assertTrue(readiness.score in 20..45)
        assertEquals("Recuperación", readiness.level)
        assertTrue(readiness.message.contains("recuperación") || readiness.message.contains("suave"))
        assertTrue(readiness.reasons.any { it.contains("hidratación") })
        assertTrue(readiness.reasons.any { it.contains("fatiga") })
    }

    @Test
    fun `raises score for balanced local records`() {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = listOf(
                HealthLogEntity(
                    waterIntakeMl = 2200,
                    sleepDurationHours = 7.5f,
                    energyLevel = 8,
                    painFatigueLevel = 1,
                    timestamp = now
                )
            ),
            todayFoodLogs = listOf(
                food("Avena", "Desayuno", 350, now),
                food("Pollo", "Comida", 520, now)
            ),
            allHealthLogs = listOf(HealthLogEntity(weightKg = 76f, timestamp = now - day)),
            workouts = listOf(workout(now - (2 * day))),
            activitySessions = emptyList(),
            recommendedMuscle = Muscle.BACK,
            discipline = UserDiscipline.GYM,
            nowMillis = now
        )

        val readiness = ReadinessScoreRules.calculate(monitor)

        assertTrue(readiness.score >= 78)
        assertEquals("Listo", readiness.level)
        assertTrue(readiness.message.contains("buen día") || readiness.message.contains("moderada"))
    }

    private fun workout(timestamp: Long): WorkoutLogEntity =
        WorkoutLogEntity(
            routineName = "Fuerza",
            durationMinutes = 45,
            caloriesBurned = 240,
            exercisesText = "sentadilla, remo",
            timestamp = timestamp
        )

    private fun food(name: String, type: String, calories: Int, timestamp: Long): FoodLogEntity =
        FoodLogEntity(
            mealName = name,
            mealType = type,
            calories = calories,
            proteinG = 20f,
            carbsG = 40f,
            fatG = 10f,
            timestamp = timestamp
        )
}
