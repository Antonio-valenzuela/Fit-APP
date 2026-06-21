package com.example

import com.example.data.local.DevicePerformanceUtils
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.VisualPerformanceProfile
import com.example.ui.components.DashboardWidgetId
import com.example.ui.components.DashboardWidgetManifestResolver
import com.example.ui.components.WidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardWidgetManifestTest {

    private val profile = VisualPerformanceProfile(
        deviceTier = DevicePerformanceUtils.DeviceTier.MID_RANGE,
        animationsEnabled = true,
        advancedAnimationsEnabled = false,
        gradientsEnabled = true,
        shadowsEnabled = true,
        blurEnabled = false,
        glassmorphismEnabled = false,
        chartsEnabled = true,
        advancedChartsEnabled = false,
        maxDashboardWidgets = 7,
        maxHistoryItems = 25,
        useLightweightCards = false,
        loadDataOnDemand = true
    )

    @Test
    fun freeUserGetsLockedAiCoachButCoreWidgetsRemainVisible() {
        val manifest = DashboardWidgetManifestResolver.resolve(
            user = UserEntity(name = "Yahir"),
            isPremium = false,
            todayMeals = emptyList(),
            todayHealth = emptyList(),
            recentHealth = emptyList(),
            profile = profile
        )

        assertEquals(
            WidgetState.LOCKED,
            DashboardWidgetManifestResolver.stateOf(manifest, DashboardWidgetId.AI_COACH)
        )
        assertTrue(manifest.any { it.id == DashboardWidgetId.TODAY_PLAN && it.systemPinned })
        assertTrue(manifest.any { it.id == DashboardWidgetId.WORKOUT && it.systemPinned })
    }

    @Test
    fun progressWidgetBecomesActiveWhenWeightDataExists() {
        val manifest = DashboardWidgetManifestResolver.resolve(
            user = UserEntity(name = "Yahir"),
            isPremium = true,
            todayMeals = emptyList(),
            todayHealth = emptyList(),
            recentHealth = listOf(HealthLogEntity(weightKg = 76f)),
            profile = profile
        )

        assertEquals(
            WidgetState.ACTIVE,
            DashboardWidgetManifestResolver.stateOf(manifest, DashboardWidgetId.PROGRESS)
        )
    }
}
