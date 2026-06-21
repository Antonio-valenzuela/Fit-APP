package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppPerformanceMode
import com.example.data.model.PerformanceUserPreferences
import com.example.data.model.VisualPerformanceProfile
import com.example.data.model.buildVisualPerformanceProfile

class PerformanceSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getPreferences(): PerformanceUserPreferences {
        return PerformanceUserPreferences(
            selectedMode = runCatching {
                AppPerformanceMode.valueOf(
                    prefs.getString(KEY_SELECTED_MODE, AppPerformanceMode.AUTO.name)
                        ?: AppPerformanceMode.AUTO.name
                )
            }.getOrDefault(AppPerformanceMode.AUTO),
            animationsDisabled = prefs.getBoolean(KEY_ANIMATIONS_DISABLED, false),
            chartsDisabled = prefs.getBoolean(KEY_CHARTS_DISABLED, false),
            visualEffectsDisabled = prefs.getBoolean(KEY_VISUAL_EFFECTS_DISABLED, false),
            dashboardWidgetLimit = prefs.getNullableInt(KEY_DASHBOARD_WIDGET_LIMIT),
            historyItemLimit = prefs.getNullableInt(KEY_HISTORY_ITEM_LIMIT),
            batterySaverEnabled = prefs.getBoolean(KEY_BATTERY_SAVER_ENABLED, false)
        )
    }

    fun buildProfile(): VisualPerformanceProfile {
        return buildVisualPerformanceProfile(appContext, getPreferences())
    }

    fun setSelectedMode(mode: AppPerformanceMode) {
        prefs.edit().putString(KEY_SELECTED_MODE, mode.name).apply()
    }

    fun setAnimationsDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS_DISABLED, disabled).apply()
    }

    fun setChartsDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHARTS_DISABLED, disabled).apply()
    }

    fun setVisualEffectsDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_VISUAL_EFFECTS_DISABLED, disabled).apply()
    }

    fun setDashboardWidgetLimit(limit: Int?) {
        prefs.edit().putNullableInt(KEY_DASHBOARD_WIDGET_LIMIT, limit?.coerceIn(3, 10)).apply()
    }

    fun setHistoryItemLimit(limit: Int?) {
        prefs.edit().putNullableInt(KEY_HISTORY_ITEM_LIMIT, limit?.coerceIn(5, 50)).apply()
    }

    fun setBatterySaverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_SAVER_ENABLED, enabled).apply()
    }

    fun resetToAutomatic() {
        prefs.edit()
            .putString(KEY_SELECTED_MODE, AppPerformanceMode.AUTO.name)
            .putBoolean(KEY_ANIMATIONS_DISABLED, false)
            .putBoolean(KEY_CHARTS_DISABLED, false)
            .putBoolean(KEY_VISUAL_EFFECTS_DISABLED, false)
            .remove(KEY_DASHBOARD_WIDGET_LIMIT)
            .remove(KEY_HISTORY_ITEM_LIMIT)
            .putBoolean(KEY_BATTERY_SAVER_ENABLED, false)
            .apply()
    }

    private fun SharedPreferences.getNullableInt(key: String): Int? {
        return if (contains(key)) getInt(key, 0) else null
    }

    private fun SharedPreferences.Editor.putNullableInt(
        key: String,
        value: Int?
    ): SharedPreferences.Editor {
        return if (value == null) remove(key) else putInt(key, value)
    }

    private companion object {
        const val PREFS_NAME = "fitplan_mx_performance_preferences"
        const val KEY_SELECTED_MODE = "selected_mode"
        const val KEY_ANIMATIONS_DISABLED = "animations_disabled"
        const val KEY_CHARTS_DISABLED = "charts_disabled"
        const val KEY_VISUAL_EFFECTS_DISABLED = "visual_effects_disabled"
        const val KEY_DASHBOARD_WIDGET_LIMIT = "dashboard_widget_limit"
        const val KEY_HISTORY_ITEM_LIMIT = "history_item_limit"
        const val KEY_BATTERY_SAVER_ENABLED = "battery_saver_enabled"
    }
}
