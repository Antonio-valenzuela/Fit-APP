package com.example.ui.workout

import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCatalogFiltersTest {
    @Test
    fun `filters routines by available time without dead results`() {
        val routines = TrainingCatalog.filteredRoutines(
            maxMinutes = 15,
            equipment = "Sin equipo",
            objective = "Bajar grasa"
        )

        assertTrue(routines.isNotEmpty())
        assertTrue(routines.all { it.estimatedMinutes <= 15 })
    }

    @Test
    fun `gym filter returns routines that can be started`() {
        val routines = TrainingCatalog.filteredRoutines(
            maxMinutes = 45,
            equipment = "Gym",
            objective = "Hipertrofia"
        )

        assertTrue(routines.isNotEmpty())
        assertTrue(routines.all { it.exerciseIds.isNotEmpty() })
    }
}
