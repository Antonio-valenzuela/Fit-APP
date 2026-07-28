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

    @Test
    fun `gym profile does not fall back to home routines when time is shorter than catalog`() {
        val routines = TrainingCatalog.filteredRoutines(
            maxMinutes = 30,
            equipment = "Gym",
            objective = "Fuerza"
        )

        assertTrue(routines.isNotEmpty())
        assertTrue(routines.all { it.equipment == "Gym" })
    }

    @Test
    fun `beginner profile never receives intermediate routines`() {
        val routines = TrainingCatalog.filteredRoutines(
            maxMinutes = 60,
            equipment = "Ambos",
            objective = "Fuerza",
            level = "Principiante"
        )

        assertTrue(routines.isNotEmpty())
        assertTrue(routines.all { it.level == "Principiante" })
    }

    @Test
    fun `both training places include home and gym routines`() {
        val routines = TrainingCatalog.filteredRoutines(
            maxMinutes = 60,
            equipment = "Ambos",
            objective = "Sin preferencia",
            level = "Avanzado"
        )

        assertTrue(routines.any { it.equipment == "Gym" })
        assertTrue(routines.any { it.equipment == "Casa" || it.equipment == "Sin equipo" })
    }
}
