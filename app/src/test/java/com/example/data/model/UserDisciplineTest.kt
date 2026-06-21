package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDisciplineTest {
    @Test
    fun disciplinesExposeStableRoutesForPrimaryActions() {
        assertEquals("workout", UserDiscipline.GYM.primaryRoute)
        assertEquals("active_run", UserDiscipline.RUNNING.primaryRoute)
        assertEquals("active_ride", UserDiscipline.CYCLING.primaryRoute)
        assertEquals("wellness_diary", UserDiscipline.WELLNESS.primaryRoute)
        assertEquals("daily_plan", UserDiscipline.HYBRID.primaryRoute)
    }

    @Test
    fun fromStoredNameFallsBackToGymForUnknownValues() {
        assertEquals(UserDiscipline.CYCLING, UserDiscipline.fromStoredName("CYCLING"))
        assertEquals(UserDiscipline.GYM, UserDiscipline.fromStoredName("not-real"))
        assertEquals(UserDiscipline.GYM, UserDiscipline.fromStoredName(""))
    }

    @Test
    fun cardioSessionSummaryUsesOnlyRealDistanceAndDuration() {
        val emptySummary = ActivitySessionSummaryRules.summarize(emptyList())
        assertFalse(emptySummary.hasDistance)
        assertEquals(null, emptySummary.distanceText)
        assertEquals(null, emptySummary.paceText)

        val summary = ActivitySessionSummaryRules.summarize(
            listOf(
                ActivitySessionEntity(
                    discipline = UserDiscipline.RUNNING.name,
                    title = "Carrera",
                    distanceKm = 5.0f,
                    durationMinutes = 30,
                    timestamp = 2000L
                ),
                ActivitySessionEntity(
                    discipline = UserDiscipline.RUNNING.name,
                    title = "Caminata",
                    distanceKm = 2.0f,
                    durationMinutes = 20,
                    timestamp = 1000L
                )
            )
        )

        assertTrue(summary.hasDistance)
        assertEquals("7.0 km", summary.distanceText)
        assertEquals("7.1 min/km", summary.paceText)
        assertEquals("2 sesiones", summary.sessionCountText)
    }

    @Test
    fun cardioSessionSummaryUsesGpsSecondsWhenMinutesRoundToZero() {
        val summary = ActivitySessionSummaryRules.summarize(
            listOf(
                ActivitySessionEntity(
                    discipline = UserDiscipline.CYCLING.name,
                    title = "Rodada GPS",
                    distanceKm = 0.76f,
                    durationMinutes = 0,
                    durationSeconds = 26L,
                    averageSpeedKmh = 105.8,
                    timestamp = 3000L
                )
            )
        )

        assertTrue(summary.hasDistance)
        assertEquals("0.8 km", summary.distanceText)
        assertEquals("105.2 km/h", summary.speedText)
        assertEquals("0.6 min/km", summary.paceText)
    }
}
