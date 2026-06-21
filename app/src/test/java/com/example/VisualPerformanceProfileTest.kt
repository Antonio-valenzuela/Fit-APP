package com.example

import com.example.data.local.DevicePerformanceUtils
import com.example.data.model.AppPerformanceMode
import com.example.data.model.PerformanceUserPreferences
import com.example.data.model.VisualPerformanceProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPerformanceProfileTest {
    @Test
    fun automaticModeAppliesDetectedLowEndProfile() {
        val profile = VisualPerformanceProfiles.forMode(
            detectedTier = DevicePerformanceUtils.DeviceTier.LOW_END,
            selectedMode = AppPerformanceMode.AUTO
        )

        assertFalse(profile.animationsEnabled)
        assertFalse(profile.chartsEnabled)
        assertEquals(10, profile.maxHistoryItems)
        assertTrue(profile.useLightweightCards)
    }

    @Test
    fun manualLightweightModeIsRespectedOnHighEndDevice() {
        val profile = VisualPerformanceProfiles.forMode(
            detectedTier = DevicePerformanceUtils.DeviceTier.HIGH_END,
            selectedMode = AppPerformanceMode.LIGHTWEIGHT
        )

        assertFalse(profile.animationsEnabled)
        assertFalse(profile.advancedChartsEnabled)
        assertEquals(5, profile.maxDashboardWidgets)
        assertTrue(profile.loadDataOnDemand)
    }

    @Test
    fun manualPremiumModeEnablesPremiumVisualProfile() {
        val profile = VisualPerformanceProfiles.forMode(
            detectedTier = DevicePerformanceUtils.DeviceTier.MID_RANGE,
            selectedMode = AppPerformanceMode.VISUAL_HIGH
        )

        assertTrue(profile.animationsEnabled)
        assertTrue(profile.advancedAnimationsEnabled)
        assertTrue(profile.gradientsEnabled)
        assertTrue(profile.advancedChartsEnabled)
    }

    @Test
    fun userCanDisableAnimationsOverAnyProfile() {
        val profile = VisualPerformanceProfiles.applyUserPreferences(
            baseProfile = VisualPerformanceProfiles.highEnd,
            preferences = PerformanceUserPreferences(animationsDisabled = true)
        )

        assertFalse(profile.animationsEnabled)
        assertFalse(profile.advancedAnimationsEnabled)
    }

    @Test
    fun lowEndProfileLimitsHistory() {
        assertEquals(10, VisualPerformanceProfiles.lowEnd.maxHistoryItems)
    }
}
