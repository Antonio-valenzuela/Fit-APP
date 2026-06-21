package com.example.data.model

import android.content.Context
import com.example.data.local.DevicePerformanceUtils

enum class AppPerformanceMode {
    AUTO,
    VISUAL_HIGH,
    BALANCED,
    LIGHTWEIGHT
}

data class VisualPerformanceProfile(
    val deviceTier: DevicePerformanceUtils.DeviceTier,
    val animationsEnabled: Boolean,
    val advancedAnimationsEnabled: Boolean,
    val gradientsEnabled: Boolean,
    val shadowsEnabled: Boolean,
    val blurEnabled: Boolean,
    val glassmorphismEnabled: Boolean,
    val chartsEnabled: Boolean,
    val advancedChartsEnabled: Boolean,
    val maxDashboardWidgets: Int,
    val maxHistoryItems: Int,
    val useLightweightCards: Boolean,
    val loadDataOnDemand: Boolean
)

data class PerformanceUserPreferences(
    val selectedMode: AppPerformanceMode = AppPerformanceMode.AUTO,
    val animationsDisabled: Boolean = false,
    val chartsDisabled: Boolean = false,
    val visualEffectsDisabled: Boolean = false,
    val dashboardWidgetLimit: Int? = null,
    val historyItemLimit: Int? = null,
    val batterySaverEnabled: Boolean = false
)

object VisualPerformanceProfiles {
    val highEnd = VisualPerformanceProfile(
        deviceTier = DevicePerformanceUtils.DeviceTier.HIGH_END,
        animationsEnabled = true,
        advancedAnimationsEnabled = true,
        gradientsEnabled = true,
        shadowsEnabled = true,
        blurEnabled = true,
        glassmorphismEnabled = true,
        chartsEnabled = true,
        advancedChartsEnabled = true,
        maxDashboardWidgets = 10,
        maxHistoryItems = 50,
        useLightweightCards = false,
        loadDataOnDemand = false
    )

    val midRange = VisualPerformanceProfile(
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

    val lowEnd = VisualPerformanceProfile(
        deviceTier = DevicePerformanceUtils.DeviceTier.LOW_END,
        animationsEnabled = false,
        advancedAnimationsEnabled = false,
        gradientsEnabled = false,
        shadowsEnabled = false,
        blurEnabled = false,
        glassmorphismEnabled = false,
        chartsEnabled = false,
        advancedChartsEnabled = false,
        maxDashboardWidgets = 5,
        maxHistoryItems = 10,
        useLightweightCards = true,
        loadDataOnDemand = true
    )

    fun forTier(tier: DevicePerformanceUtils.DeviceTier): VisualPerformanceProfile {
        return when (tier) {
            DevicePerformanceUtils.DeviceTier.LOW_END -> lowEnd
            DevicePerformanceUtils.DeviceTier.MID_RANGE -> midRange
            DevicePerformanceUtils.DeviceTier.HIGH_END -> highEnd
        }
    }

    fun forMode(
        detectedTier: DevicePerformanceUtils.DeviceTier,
        selectedMode: AppPerformanceMode
    ): VisualPerformanceProfile {
        return when (selectedMode) {
            AppPerformanceMode.AUTO -> forTier(detectedTier)
            AppPerformanceMode.VISUAL_HIGH -> highEnd.copy(deviceTier = detectedTier)
            AppPerformanceMode.BALANCED -> midRange.copy(deviceTier = detectedTier)
            AppPerformanceMode.LIGHTWEIGHT -> lowEnd.copy(deviceTier = detectedTier)
        }
    }

    fun applyUserPreferences(
        baseProfile: VisualPerformanceProfile,
        preferences: PerformanceUserPreferences
    ): VisualPerformanceProfile {
        val batterySaverProfile = if (preferences.batterySaverEnabled) {
            baseProfile.copy(
                animationsEnabled = false,
                advancedAnimationsEnabled = false,
                blurEnabled = false,
                glassmorphismEnabled = false,
                shadowsEnabled = false,
                gradientsEnabled = false,
                advancedChartsEnabled = false,
                maxDashboardWidgets = minOf(baseProfile.maxDashboardWidgets, lowEnd.maxDashboardWidgets),
                maxHistoryItems = minOf(baseProfile.maxHistoryItems, lowEnd.maxHistoryItems),
                useLightweightCards = true,
                loadDataOnDemand = true
            )
        } else {
            baseProfile
        }

        return batterySaverProfile.copy(
            animationsEnabled = batterySaverProfile.animationsEnabled && !preferences.animationsDisabled,
            advancedAnimationsEnabled = batterySaverProfile.advancedAnimationsEnabled && !preferences.animationsDisabled,
            gradientsEnabled = batterySaverProfile.gradientsEnabled && !preferences.visualEffectsDisabled,
            shadowsEnabled = batterySaverProfile.shadowsEnabled && !preferences.visualEffectsDisabled,
            blurEnabled = batterySaverProfile.blurEnabled && !preferences.visualEffectsDisabled,
            glassmorphismEnabled = batterySaverProfile.glassmorphismEnabled && !preferences.visualEffectsDisabled,
            chartsEnabled = batterySaverProfile.chartsEnabled && !preferences.chartsDisabled,
            advancedChartsEnabled = batterySaverProfile.advancedChartsEnabled &&
                !preferences.chartsDisabled &&
                !preferences.visualEffectsDisabled,
            maxDashboardWidgets = preferences.dashboardWidgetLimit
                ?.coerceIn(3, highEnd.maxDashboardWidgets)
                ?: batterySaverProfile.maxDashboardWidgets,
            maxHistoryItems = preferences.historyItemLimit
                ?.coerceIn(5, highEnd.maxHistoryItems)
                ?: batterySaverProfile.maxHistoryItems,
            useLightweightCards = batterySaverProfile.useLightweightCards || preferences.visualEffectsDisabled,
            loadDataOnDemand = batterySaverProfile.loadDataOnDemand || preferences.batterySaverEnabled
        )
    }
}

fun buildVisualPerformanceProfile(
    context: Context,
    selectedMode: AppPerformanceMode
): VisualPerformanceProfile {
    val detectedTier = DevicePerformanceUtils.detectDeviceTier(context)
    return VisualPerformanceProfiles.forMode(detectedTier, selectedMode)
}

fun buildVisualPerformanceProfile(
    context: Context,
    preferences: PerformanceUserPreferences
): VisualPerformanceProfile {
    val baseProfile = buildVisualPerformanceProfile(context, preferences.selectedMode)
    return VisualPerformanceProfiles.applyUserPreferences(baseProfile, preferences)
}
