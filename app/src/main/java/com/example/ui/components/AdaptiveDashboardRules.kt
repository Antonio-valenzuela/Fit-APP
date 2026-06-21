package com.example.ui.components

import com.example.data.model.FeatureSettings
import com.example.data.model.VisualPerformanceProfile

object AdaptiveDashboardRules {
    fun shouldRenderCharts(
        profile: VisualPerformanceProfile,
        featureSettings: FeatureSettings
    ): Boolean {
        return featureSettings.chartsEnabled && profile.chartsEnabled
    }

    fun shouldUseLightweightWidgets(profile: VisualPerformanceProfile): Boolean {
        return profile.useLightweightCards
    }

    fun shouldLoadNutrition(featureSettings: FeatureSettings): Boolean {
        return featureSettings.nutritionEnabled
    }

    fun shouldLoadTodayHealth(featureSettings: FeatureSettings): Boolean {
        return featureSettings.healthEnabled
    }

    fun shouldLoadProgressHistory(featureSettings: FeatureSettings): Boolean {
        return featureSettings.progressEnabled
    }

    fun historyLimit(profile: VisualPerformanceProfile): Int {
        return profile.maxHistoryItems
    }

    fun dashboardWidgetLimit(profile: VisualPerformanceProfile): Int {
        return profile.maxDashboardWidgets
    }
}
