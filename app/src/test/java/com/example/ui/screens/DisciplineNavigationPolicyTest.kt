package com.example.ui.screens

import com.example.data.model.UserDiscipline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineNavigationPolicyTest {
    @Test
    fun gymBottomNavigationPrioritizesFoodInsteadOfProgress() {
        val items = DisciplineNavigationPolicy.bottomItemsFor(UserDiscipline.GYM)
        val routes = items.map { it.route }

        assertEquals(5, items.size)
        assertTrue(routes.contains(Destinations.NUTRITION))
        assertFalse(routes.contains(Destinations.PROGRESS))
        assertEquals("Comida", items.first { it.route == Destinations.NUTRITION }.label)
    }

    @Test
    fun gymMuscleMapDoesNotShareProgressTab() {
        val items = DisciplineNavigationPolicy.bottomItemsFor(UserDiscipline.GYM)
        val muscleItem = items.first { it.route == Destinations.MUSCLE_MAP }

        assertEquals("Musculos", muscleItem.label)
        assertFalse(muscleItem.selectedRoutes.contains(Destinations.PROGRESS))
        assertFalse(items.any { item ->
            item.selectedRoutes.contains(Destinations.MUSCLE_MAP) &&
                item.selectedRoutes.contains(Destinations.PROGRESS)
        })
    }

    @Test
    fun hybridCardioTabIsActiveForRunAndRideTrackers() {
        val items = DisciplineNavigationPolicy.bottomItemsFor(UserDiscipline.HYBRID)
        val cardioItem = items.first { it.label == "Cardio" }

        assertEquals(Destinations.ACTIVE_RUN, cardioItem.route)
        assertTrue(cardioItem.selectedRoutes.contains(Destinations.ACTIVE_RUN))
        assertTrue(cardioItem.selectedRoutes.contains(Destinations.ACTIVE_RIDE))
    }
}
