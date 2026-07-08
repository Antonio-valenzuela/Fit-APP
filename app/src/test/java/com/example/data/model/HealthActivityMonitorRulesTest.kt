package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthActivityMonitorRulesTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `detects missing records low hydration inactivity and neglected muscle`() {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = listOf(HealthLogEntity(waterIntakeMl = 600, timestamp = now)),
            todayFoodLogs = emptyList(),
            allHealthLogs = emptyList(),
            workouts = listOf(
                WorkoutLogEntity(
                    routineName = "Torso",
                    durationMinutes = 45,
                    caloriesBurned = 250,
                    exercisesText = "press banca, remo",
                    timestamp = now - (5 * day)
                )
            ),
            activitySessions = emptyList(),
            recommendedMuscle = Muscle.SHOULDERS,
            discipline = UserDiscipline.GYM,
            nowMillis = now
        )

        assertEquals(HydrationSignal.LOW, monitor.hydrationSignal)
        assertTrue(monitor.missingRecords.contains(MissingRecord.FOOD))
        assertTrue(monitor.missingRecords.contains(MissingRecord.WEIGHT))
        assertEquals(5, monitor.daysSinceLastTraining)
        assertTrue(monitor.hasLowActivity)
        assertTrue(monitor.recentlyWorkedMuscles.contains(Muscle.CHEST))
        assertTrue(monitor.recentlyWorkedMuscles.contains(Muscle.BACK))
        assertTrue(monitor.neglectedMuscles.contains(Muscle.SHOULDERS))
        assertEquals(OvertrainingRisk.LOW, monitor.overtrainingRisk)
    }

    @Test
    fun `detects basic overtraining risk from dense training fatigue and low sleep`() {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = listOf(
                HealthLogEntity(
                    waterIntakeMl = 1800,
                    sleepDurationHours = 5.0f,
                    painFatigueLevel = 8,
                    energyLevel = 3,
                    timestamp = now
                )
            ),
            todayFoodLogs = listOf(
                food("Avena", "Desayuno", 350, now),
                food("Pollo", "Comida", 520, now)
            ),
            allHealthLogs = listOf(HealthLogEntity(weightKg = 78f, timestamp = now - day)),
            workouts = listOf(
                workout("Pierna", "sentadilla, peso muerto", now - (1 * day)),
                workout("Torso", "press banca, dominadas", now - (2 * day)),
                workout("Brazos", "curl biceps, triceps", now - (3 * day)),
                workout("Hombro", "press militar", now - (4 * day))
            ),
            activitySessions = listOf(
                ActivitySessionEntity(
                    discipline = UserDiscipline.RUNNING.name,
                    title = "Carrera suave",
                    durationMinutes = 30,
                    timestamp = now - (2 * day)
                )
            ),
            recommendedMuscle = null,
            discipline = UserDiscipline.HYBRID,
            nowMillis = now
        )

        assertEquals(OvertrainingRisk.HIGH, monitor.overtrainingRisk)
        assertEquals(5, monitor.trainingSessionsLast7Days)
        assertEquals(5.0f, monitor.sleepHours ?: 0f)
        assertEquals(8, monitor.fatigueLevel)
        assertTrue(monitor.recentlyWorkedMuscles.contains(Muscle.QUADS))
        assertTrue(monitor.recentlyWorkedMuscles.contains(Muscle.HAMSTRINGS))
    }

    private fun workout(name: String, exercises: String, timestamp: Long): WorkoutLogEntity =
        WorkoutLogEntity(
            routineName = name,
            durationMinutes = 45,
            caloriesBurned = 240,
            exercisesText = exercises,
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
