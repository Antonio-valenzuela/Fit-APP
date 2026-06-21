package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessEntryRulesTest {
    @Test
    fun waterSummarySumsMlAndCapsProgress() {
        val entries = listOf(
            HealthLogEntity(type = WellnessEntryType.WATER.key, value = 250f, unit = "ml", waterIntakeMl = 250),
            HealthLogEntity(type = WellnessEntryType.WATER.key, value = 1000f, unit = "ml", waterIntakeMl = 1000),
            HealthLogEntity(type = WellnessEntryType.WATER.key, value = 1000f, unit = "ml", waterIntakeMl = 1000)
        )

        val summary = WellnessEntryRules.buildTodaySummary(entries, hasWorkoutToday = false)

        assertEquals(2250, summary.waterMl)
        assertEquals(1f, summary.waterProgress, 0.001f)
        assertEquals("Meta cumplida", summary.waterStatus)
    }

    @Test
    fun latestGlucoseUsesContextInDisplayText() {
        val entries = listOf(
            HealthLogEntity(
                type = WellnessEntryType.GLUCOSE.key,
                value = 118f,
                unit = "mg/dL",
                context = "antes de comer",
                glucoseLevel = 118f,
                timestamp = 2000L
            ),
            HealthLogEntity(
                type = WellnessEntryType.GLUCOSE.key,
                value = 100f,
                unit = "mg/dL",
                context = "en ayunas",
                glucoseLevel = 100f,
                timestamp = 1000L
            )
        )

        val summary = WellnessEntryRules.buildTodaySummary(entries, hasWorkoutToday = false)

        assertEquals("Glucosa: 118 mg/dL (antes de comer)", summary.latestGlucoseText)
    }

    @Test
    fun pressureRequiresBothNumbers() {
        val result = WellnessEntryRules.validate(
            WellnessEntryDraft(
                type = WellnessEntryType.PRESSURE,
                valueText = "120",
                secondaryValueText = "",
                unit = "mmHg"
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("diastolica") })
    }

    @Test
    fun foodAllowsVisualPortionWithoutCalories() {
        val result = WellnessEntryRules.validate(
            WellnessEntryDraft(
                type = WellnessEntryType.FOOD,
                context = "Plato normal",
                note = "despues de entrenar"
            )
        )

        assertTrue(result.errors.joinToString(), result.isValid)
    }
}
