package com.example

import com.example.data.model.FeatureSettings
import com.example.data.model.VisualPerformanceProfiles
import com.example.ui.components.AdaptiveDashboardRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDashboardRulesTest {
    @Test
    fun rendersChartsOnlyWhenFeatureAndProfileAllowIt() {
        assertTrue(
            AdaptiveDashboardRules.shouldRenderCharts(
                profile = VisualPerformanceProfiles.highEnd,
                featureSettings = FeatureSettings(chartsEnabled = true)
            )
        )

        assertFalse(
            AdaptiveDashboardRules.shouldRenderCharts(
                profile = VisualPerformanceProfiles.lowEnd,
                featureSettings = FeatureSettings(chartsEnabled = true)
            )
        )

        assertFalse(
            AdaptiveDashboardRules.shouldRenderCharts(
                profile = VisualPerformanceProfiles.highEnd,
                featureSettings = FeatureSettings(chartsEnabled = false)
            )
        )
    }

    @Test
    fun lightweightProfileUsesLightweightWidgets() {
        assertTrue(AdaptiveDashboardRules.shouldUseLightweightWidgets(VisualPerformanceProfiles.lowEnd))
    }

    @Test
    fun disabledFeaturesDoNotRequestDataLoads() {
        val disabled = FeatureSettings(
            nutritionEnabled = false,
            progressEnabled = false,
            healthEnabled = false
        )

        assertFalse(AdaptiveDashboardRules.shouldLoadNutrition(disabled))
        assertFalse(AdaptiveDashboardRules.shouldLoadTodayHealth(disabled))
        assertFalse(AdaptiveDashboardRules.shouldLoadProgressHistory(disabled))
    }
}
