package com.example.ui.catalog

import com.example.data.model.ExerciseBodyRegion
import com.example.data.model.ExerciseCatalogLocationFilter
import com.example.data.model.ExerciseEquipmentType
import com.example.data.model.Muscle
import com.example.ui.workout.TrainingCatalog
import com.example.ui.workout.ExerciseCatalogItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingCatalogFallbackPolicyTest {
    private val benchAndDumbbells = ExerciseCatalogItem(
        id = "bulgarian_squat",
        name = "Sentadilla bulgara",
        primaryMuscle = "gluteos",
        secondaryMuscles = listOf("cuadriceps", "femorales"),
        equipment = "Banco y mancuernas",
        level = "Intermedio",
        instructions = "Baja con control.",
        suggestedSets = 3,
        suggestedReps = "8-10",
        restSeconds = 90
    )

    @Test
    fun `home fallback requires every cumulative equipment item`() {
        val dumbbellsOnly = ExerciseCatalogUiState(
            location = ExerciseCatalogLocationFilter.HOME,
            availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL)
        )
        val completeEquipment = dumbbellsOnly.copy(
            availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH)
        )

        assertFalse(TrainingCatalogFallbackPolicy.matches(benchAndDumbbells, dumbbellsOnly))
        assertTrue(TrainingCatalogFallbackPolicy.matches(benchAndDumbbells, completeEquipment))
    }

    @Test
    fun `home fallback uses the same exact built in equipment registry`() {
        val noEquipment = ExerciseCatalogUiState(location = ExerciseCatalogLocationFilter.HOME)
        val dumbbells = noEquipment.copy(
            availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL)
        )
        val dumbbellsAndBench = noEquipment.copy(
            availableEquipment = setOf(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH)
        )

        assertEquals(7, TrainingCatalog.exercises.count { TrainingCatalogFallbackPolicy.matches(it, noEquipment) })
        assertEquals(17, TrainingCatalog.exercises.count { TrainingCatalogFallbackPolicy.matches(it, dumbbells) })
        assertEquals(19, TrainingCatalog.exercises.count { TrainingCatalogFallbackPolicy.matches(it, dumbbellsAndBench) })
        assertFalse(
            TrainingCatalogFallbackPolicy.matches(
                TrainingCatalog.exerciseById("leg_extension")!!,
                noEquipment
            )
        )
    }

    @Test
    fun `fallback respects muscle body region and equipment filters`() {
        val matching = ExerciseCatalogUiState(
            muscle = Muscle.GLUTES,
            bodyRegion = ExerciseBodyRegion.UPPER_LEGS,
            equipment = ExerciseEquipmentType.BENCH
        )

        assertTrue(TrainingCatalogFallbackPolicy.matches(benchAndDumbbells, matching))
        assertFalse(
            TrainingCatalogFallbackPolicy.matches(
                benchAndDumbbells,
                matching.copy(muscle = Muscle.CHEST)
            )
        )
        assertFalse(
            TrainingCatalogFallbackPolicy.matches(
                benchAndDumbbells,
                matching.copy(bodyRegion = ExerciseBodyRegion.CHEST)
            )
        )
        assertFalse(
            TrainingCatalogFallbackPolicy.matches(
                benchAndDumbbells,
                matching.copy(equipment = ExerciseEquipmentType.CABLE)
            )
        )
    }

    @Test
    fun `fallback keeps required frequent Spanish aliases searchable`() {
        val pushUp = benchAndDumbbells.copy(
            id = "push_up",
            name = "Push up",
            primaryMuscle = "pecho",
            secondaryMuscles = listOf("triceps", "abdomen"),
            equipment = "Peso corporal"
        )

        assertTrue(
            TrainingCatalogFallbackPolicy.matches(
                pushUp,
                ExerciseCatalogUiState(query = "flexiones")
            )
        )
    }

    @Test
    fun `fallback detail reports legacy physical requirements and location`() {
        assertEquals(
            listOf("Banco y mancuernas"),
            TrainingCatalogFallbackPolicy.detailEquipmentRequirements(benchAndDumbbells)
        )
        assertEquals(
            "Casa equipada o gimnasio",
            TrainingCatalogFallbackPolicy.locationDescription(benchAndDumbbells)
        )

        val bodyWeight = TrainingCatalog.exerciseById("push_up")!!
        assertTrue(TrainingCatalogFallbackPolicy.detailEquipmentRequirements(bodyWeight).isEmpty())
        assertEquals("Casa y gimnasio", TrainingCatalogFallbackPolicy.locationDescription(bodyWeight))
    }
}
