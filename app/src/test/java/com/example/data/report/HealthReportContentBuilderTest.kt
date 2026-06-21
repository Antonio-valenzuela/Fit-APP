package com.example.data.report

import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthReportContentBuilderTest {
    @Test
    fun reportContentIncludesLegalDisclaimerAndClinicalSections() {
        val now = 1_700_000_000_000L
        val content = HealthReportContentBuilder.build(
            user = UserEntity(name = "Yahir", goal = "Mantener Peso", targetCalories = 2200),
            foodLogs = listOf(
                FoodLogEntity(mealName = "Comida", mealType = "Comida", calories = 600, proteinG = 35f, carbsG = 70f, fatG = 18f, timestamp = now)
            ),
            workoutLogs = listOf(
                WorkoutLogEntity(routineName = "Pierna", durationMinutes = 45, caloriesBurned = 280, exercisesText = "Sentadilla", timestamp = now)
            ),
            healthLogs = listOf(
                HealthLogEntity(weightKg = 72.5f, timestamp = now),
                HealthLogEntity(waterIntakeMl = 1000, timestamp = now)
            ),
            generatedAt = now
        )

        assertEquals(HealthReportContentBuilder.LEGAL_DISCLAIMER, content.legalDisclaimer)
        assertTrue(content.coverTitle.contains("Informe de seguimiento"))
        assertTrue(content.patientLine.contains("Yahir"))
        assertTrue(content.weightPoints.isNotEmpty())
        assertTrue(content.weeklyNutrition.isNotEmpty())
        assertTrue(content.activityItems.first().contains("Pierna"))
        assertTrue(content.sections.any { it.title == "Resumen semanal de nutricion" })
        assertTrue(content.sections.any { it.title == "Actividad fisica" })
    }
}
