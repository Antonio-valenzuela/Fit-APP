package com.example.data.local

import com.example.data.model.ExerciseCatalogRecordValidator
import com.example.data.model.ExerciseImportManifest
import com.example.data.model.ExerciseImportPlan
import com.example.data.model.ExerciseCatalogAliasManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogImportPolicyTest {
    @Test
    fun acceptsRecordWhenEitherLanguageHasNameAndInstructions() {
        assertTrue(ExerciseCatalogRecordValidator.isValidNameAndInstructions("Press", null, "Instrucciones", null))
        assertTrue(ExerciseCatalogRecordValidator.isValidNameAndInstructions(null, "Press", null, "Instructions"))
        assertFalse(ExerciseCatalogRecordValidator.isValidNameAndInstructions(null, null, "Instrucciones", null))
        assertFalse(ExerciseCatalogRecordValidator.isValidNameAndInstructions("Press", null, null, null))
    }

    @Test
    fun bodyWeightNeverBecomesAnEquipmentRequirement() {
        assertEquals(emptyList<String>(), ExerciseCatalogRecordValidator.requirementsFor("BODY_WEIGHT", listOf("BODY_WEIGHT")))
    }

    @Test
    fun importPlanIsNoOpOnlyForSameVersionChecksumAndCount() {
        val existing = ExerciseImportManifest("exercises_dataset", "v1", "sha", 3, "rules")
        assertEquals(ExerciseImportPlan.NO_OP, ExerciseImportPlan.decide(existing, existing))
        assertEquals(ExerciseImportPlan.IMPORT, ExerciseImportPlan.decide(null, existing))
        assertEquals(ExerciseImportPlan.ABORT_VERSION_CHANGE, ExerciseImportPlan.decide(existing, existing.copy(importVersion = "v2")))
        assertEquals(ExerciseImportPlan.ABORT_VERSION_CHANGE, ExerciseImportPlan.decide(existing, existing.copy(compactSha256 = "other")))
        assertEquals(ExerciseImportPlan.ABORT_VERSION_CHANGE, ExerciseImportPlan.decide(existing, existing.copy(recordCount = 4)))
    }

    @Test
    fun aliasManifestContainsRequiredSpanishQueries() {
        val aliases = ExerciseCatalogAliasManifest.default
        assertEquals("crunch", aliases["abdominales"])
        assertEquals("biceps_curl", aliases["curl de bíceps"])
        assertEquals("pull_up", aliases["dominadas"])
        assertEquals("bench_press", aliases["press de banca"])
        assertEquals("romanian_deadlift", aliases["peso muerto"])
        assertEquals("squat", aliases["sentadilla"])
        assertEquals("push_up", aliases["flexiones"])
        assertEquals("push_up", aliases["lagartijas"])
        assertEquals("lateral_raise", aliases["elevaciones laterales"])
        assertEquals("plank", aliases["plancha"])
        assertEquals("barbell_row", aliases["remo"])
        assertEquals("lunges", aliases["zancadas"])
    }
}
