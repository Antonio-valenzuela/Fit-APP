package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleTest {

    @Test
    fun testFromLegacyName_matchesCorrectMuscles() {
        assertEquals(Muscle.CHEST, Muscle.fromLegacyName("pecho"))
        assertEquals(Muscle.CHEST, Muscle.fromLegacyName("chest"))
        assertEquals(Muscle.CHEST, Muscle.fromLegacyName(" Pecho "))
        
        assertEquals(Muscle.BACK, Muscle.fromLegacyName("Espalda"))
        assertEquals(Muscle.BACK, Muscle.fromLegacyName("espalda"))
        
        assertEquals(Muscle.SHOULDERS, Muscle.fromLegacyName("hombro"))
        assertEquals(Muscle.SHOULDERS, Muscle.fromLegacyName("hombros"))
        
        assertEquals(Muscle.BICEPS, Muscle.fromLegacyName("biceps"))
        assertEquals(Muscle.BICEPS, Muscle.fromLegacyName("bíceps")) // with accent
        
        assertEquals(Muscle.ABS, Muscle.fromLegacyName("abdomen"))
        assertEquals(Muscle.ABS, Muscle.fromLegacyName("abs"))
        assertEquals(Muscle.ABS, Muscle.fromLegacyName("core"))
        
        assertEquals(Muscle.QUADS, Muscle.fromLegacyName("cuadriceps"))
        assertEquals(Muscle.QUADS, Muscle.fromLegacyName("cuádriceps"))
        
        assertEquals(Muscle.HAMSTRINGS, Muscle.fromLegacyName("femorales"))
        assertEquals(Muscle.HAMSTRINGS, Muscle.fromLegacyName("isquiotibiales"))
        
        assertEquals(Muscle.CALVES, Muscle.fromLegacyName("pantorrillas"))
        assertEquals(Muscle.CALVES, Muscle.fromLegacyName("gemelos"))
    }

    @Test
    fun testFromLegacyName_returnsNullForUnknown() {
        assertNull(Muscle.fromLegacyName("unknown_muscle"))
        assertNull(Muscle.fromLegacyName(""))
        assertNull(Muscle.fromLegacyName(null))
    }

    @Test
    fun testExerciseMapUsesEnum() {
        val mappedMuscles = MuscleTrainingProfiles.EXERCISE_MUSCLE_MAP["sentadilla"]
        assertEquals(true, mappedMuscles?.any { it.first == Muscle.QUADS })
    }
}
