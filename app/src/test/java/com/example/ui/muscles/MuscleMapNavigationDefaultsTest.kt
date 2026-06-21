package com.example.ui.muscles

import com.example.data.model.UserDiscipline
import com.example.ui.screens.Destinations
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleMapNavigationDefaultsTest {
    @Test
    fun muscleMapUsesItsOwnRouteAsSelectedBottomDestination() {
        assertEquals(Destinations.MUSCLE_MAP, MuscleMapNavigationDefaults.selectedRoute)
    }

    @Test
    fun muscleMapDefaultsToGymBottomNavigationWhenDisciplineIsNotProvided() {
        assertEquals(UserDiscipline.GYM, MuscleMapNavigationDefaults.defaultDiscipline)
    }
}
