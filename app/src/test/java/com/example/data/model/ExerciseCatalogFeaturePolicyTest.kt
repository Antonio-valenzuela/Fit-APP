package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogFeaturePolicyTest {
    @Test
    fun `disabled build always keeps the catalog disabled`() {
        assertFalse(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = false, storedOverride = null))
        assertFalse(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = false, storedOverride = false))
        assertFalse(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = false, storedOverride = true))
    }

    @Test
    fun `enabled build defaults on and preserves a local override`() {
        assertTrue(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = true, storedOverride = null))
        assertFalse(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = true, storedOverride = false))
        assertTrue(ExerciseCatalogFeaturePolicy.resolve(buildEnabled = true, storedOverride = true))
    }
}
