package com.example.data.model

import com.example.ui.workout.TrainingCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInExerciseEquipmentProfilesTest {
    @Test
    fun `registry contains exactly the training catalog built ins`() {
        val trainingCatalogIds = TrainingCatalog.exercises.map { it.id }.toSet()

        BuiltInExerciseEquipmentProfiles.requireExactCatalogIds(trainingCatalogIds)

        assertEquals(27, BuiltInExerciseEquipmentProfiles.byCatalogId.size)
        assertEquals(trainingCatalogIds, BuiltInExerciseEquipmentProfiles.catalogIds)
        assertTrue(BuiltInExerciseEquipmentProfiles.byCatalogId.values.all {
            it.locationCompatibility == ExerciseLocationCompatibility.BOTH
        })
        assertEquals(
            ExerciseEquipmentType.PULL_UP_BAR,
            BuiltInExerciseEquipmentProfiles.getValue("pull_up").primaryEquipmentType
        )
    }

    @Test
    fun `home compatibility has the expected no equipment and common kit counts`() {
        assertEquals(7, compatibleHomeIds().size)
        assertEquals(17, compatibleHomeIds(ExerciseEquipmentType.DUMBBELL).size)
        assertEquals(
            19,
            compatibleHomeIds(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH).size
        )
    }

    @Test
    fun `alternative equipment is OR while cumulative equipment is ALL`() {
        assertTrue("squat" in compatibleHomeIds(ExerciseEquipmentType.BARBELL))
        assertTrue("flyes" in compatibleHomeIds(ExerciseEquipmentType.CABLE))
        assertTrue("calf_raise" in compatibleHomeIds(ExerciseEquipmentType.STEP_PLATFORM))
        assertTrue("french_press" in compatibleHomeIds(ExerciseEquipmentType.EZ_BARBELL))

        assertFalse("bulgarian_squat" in compatibleHomeIds(ExerciseEquipmentType.DUMBBELL))
        assertTrue(
            "bulgarian_squat" in compatibleHomeIds(
                ExerciseEquipmentType.DUMBBELL,
                ExerciseEquipmentType.BENCH
            )
        )
        assertFalse("hip_thrust" in compatibleHomeIds(ExerciseEquipmentType.BARBELL))
        assertTrue(
            "hip_thrust" in compatibleHomeIds(
                ExerciseEquipmentType.BARBELL,
                ExerciseEquipmentType.BENCH
            )
        )
    }

    @Test
    fun `room requirement rows contain only cumulative non bodyweight equipment`() {
        assertEquals(
            setOf(ExerciseEquipmentType.DUMBBELL, ExerciseEquipmentType.BENCH),
            BuiltInExerciseEquipmentProfiles.roomRequirementsFor("bulgarian_squat").toSet()
        )
        assertTrue(BuiltInExerciseEquipmentProfiles.roomRequirementsFor("squat").isEmpty())
        assertTrue(BuiltInExerciseEquipmentProfiles.roomRequirementsFor("push_up").isEmpty())
    }

    @Test
    fun `equipment filter includes primary alternatives and cumulative requirements`() {
        assertTrue("military_press" in equipmentFilterIds(ExerciseEquipmentType.DUMBBELL))
        assertTrue("bench_press" in equipmentFilterIds(ExerciseEquipmentType.DUMBBELL))
        assertTrue("flyes" in equipmentFilterIds(ExerciseEquipmentType.CABLE))
        assertTrue("hip_thrust" in equipmentFilterIds(ExerciseEquipmentType.BENCH))
        assertTrue("pull_up" in equipmentFilterIds(ExerciseEquipmentType.PULL_UP_BAR))
        assertFalse("leg_curl" in equipmentFilterIds(ExerciseEquipmentType.DUMBBELL))
    }

    @Test
    fun `equipment filter binding never returns an empty IN list`() {
        val noFilter = BuiltInExerciseEquipmentProfiles
            .nonEmptyCatalogIdsMatchingEquipmentFilter(null)
        val equipmentWithoutBuiltIns = BuiltInExerciseEquipmentProfiles
            .nonEmptyCatalogIdsMatchingEquipmentFilter(ExerciseEquipmentType.AB_WHEEL)

        assertEquals(listOf(BuiltInExerciseEquipmentProfiles.NO_EQUIPMENT_FILTER_MATCH_ID), noFilter)
        assertEquals(
            listOf(BuiltInExerciseEquipmentProfiles.NO_EQUIPMENT_FILTER_MATCH_ID),
            equipmentWithoutBuiltIns
        )
        assertTrue(noFilter.isNotEmpty())
        assertTrue(equipmentWithoutBuiltIns.isNotEmpty())
    }

    private fun compatibleHomeIds(
        vararg equipment: ExerciseEquipmentType
    ): Set<String> = BuiltInExerciseEquipmentProfiles.compatibleHomeCatalogIds(equipment.toSet()).toSet()

    private fun equipmentFilterIds(equipment: ExerciseEquipmentType): Set<String> =
        BuiltInExerciseEquipmentProfiles.catalogIdsMatchingEquipmentFilter(equipment).toSet()
}
