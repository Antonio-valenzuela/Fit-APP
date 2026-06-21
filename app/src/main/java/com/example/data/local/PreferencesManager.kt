package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.FeatureSettings
import com.example.data.model.HybridPlanSettings

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "fitplan_mx_preferences",
        Context.MODE_PRIVATE
    )

    init {
        if (!prefs.contains("initialized_performance_defaults")) {
            val isLowEnd = DevicePerformanceUtils.isLowEndDevice(context)
            prefs.edit().apply {
                putBoolean("initialized_performance_defaults", true)
                putBoolean("feature_performance_mode_enabled", isLowEnd)
                putBoolean("feature_animations_enabled", !isLowEnd)
                putBoolean("feature_charts_enabled", !isLowEnd)
                putBoolean("feature_ai_recommendations_enabled", false)
                putBoolean("feature_reminders_enabled", false)
                putBoolean("feature_nutrition_enabled", true)
                putBoolean("feature_workout_enabled", true)
                putBoolean("feature_progress_enabled", true)
                putBoolean("feature_health_enabled", true)
                putBoolean("feature_premium_enabled", false)
                apply()
            }
        }
        if (!prefs.contains("migrated_health_manual_default_v1")) {
            prefs.edit().apply {
                putBoolean("migrated_health_manual_default_v1", true)
                putBoolean("feature_health_enabled", true)
                apply()
            }
        }
    }

    fun getFeatureSettings(): FeatureSettings {
        return try {
            FeatureSettings(
                nutritionEnabled = prefs.getBoolean("feature_nutrition_enabled", true),
                workoutEnabled = prefs.getBoolean("feature_workout_enabled", true),
                progressEnabled = prefs.getBoolean("feature_progress_enabled", true),
                healthEnabled = prefs.getBoolean("feature_health_enabled", true),
                aiRecommendationsEnabled = prefs.getBoolean("feature_ai_recommendations_enabled", false),
                premiumEnabled = prefs.getBoolean("feature_premium_enabled", false),
                chartsEnabled = prefs.getBoolean("feature_charts_enabled", true),
                remindersEnabled = prefs.getBoolean("feature_reminders_enabled", false),
                animationsEnabled = prefs.getBoolean("feature_animations_enabled", false),
                performanceModeEnabled = prefs.getBoolean("feature_performance_mode_enabled", true),
                streakEnabled = prefs.getBoolean("feature_streak_enabled", true),
                showStreakWidget = prefs.getBoolean("feature_show_streak_widget", true),
                dailyMissionsEnabled = prefs.getBoolean("feature_daily_missions_enabled", true),
                achievementsEnabled = prefs.getBoolean("feature_achievements_enabled", true),
                motivationMessagesEnabled = prefs.getBoolean("feature_motivation_messages_enabled", true),
                softMotivationMode = prefs.getBoolean("feature_soft_motivation_mode", true)
            )
        } catch (error: ClassCastException) {
            Log.e("PreferencesManager", "Feature settings were corrupted; using defaults", error)
            FeatureSettings()
        }
    }

    fun updateFeatureSettings(settings: FeatureSettings) {
        prefs.edit().apply {
            putBoolean("feature_nutrition_enabled", settings.nutritionEnabled)
            putBoolean("feature_workout_enabled", settings.workoutEnabled)
            putBoolean("feature_progress_enabled", settings.progressEnabled)
            putBoolean("feature_health_enabled", settings.healthEnabled)
            putBoolean("feature_ai_recommendations_enabled", settings.aiRecommendationsEnabled)
            putBoolean("feature_premium_enabled", settings.premiumEnabled)
            putBoolean("feature_charts_enabled", settings.chartsEnabled)
            putBoolean("feature_reminders_enabled", settings.remindersEnabled)
            putBoolean("feature_animations_enabled", settings.animationsEnabled)
            putBoolean("feature_performance_mode_enabled", settings.performanceModeEnabled)
            putBoolean("feature_streak_enabled", settings.streakEnabled)
            putBoolean("feature_show_streak_widget", settings.showStreakWidget)
            putBoolean("feature_daily_missions_enabled", settings.dailyMissionsEnabled)
            putBoolean("feature_achievements_enabled", settings.achievementsEnabled)
            putBoolean("feature_motivation_messages_enabled", settings.motivationMessagesEnabled)
            putBoolean("feature_soft_motivation_mode", settings.softMotivationMode)
            apply()
        }
    }

    fun setNutritionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_nutrition_enabled", enabled).apply()
    }

    fun setWorkoutEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_workout_enabled", enabled).apply()
    }

    fun setProgressEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_progress_enabled", enabled).apply()
    }

    fun setHealthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_health_enabled", enabled).apply()
    }

    fun setAIRecommendationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_ai_recommendations_enabled", enabled).apply()
    }

    fun setPremiumEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_premium_enabled", enabled).apply()
    }

    fun setChartsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_charts_enabled", enabled).apply()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_reminders_enabled", enabled).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_animations_enabled", enabled).apply()
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_performance_mode_enabled", enabled).apply()
    }

    fun setStreakEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_streak_enabled", enabled).apply()
    }

    fun setShowStreakWidget(enabled: Boolean) {
        prefs.edit().putBoolean("feature_show_streak_widget", enabled).apply()
    }

    fun setDailyMissionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_daily_missions_enabled", enabled).apply()
    }

    fun setAchievementsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_achievements_enabled", enabled).apply()
    }

    fun setMotivationMessagesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_motivation_messages_enabled", enabled).apply()
    }

    fun setSoftMotivationMode(enabled: Boolean) {
        prefs.edit().putBoolean("feature_soft_motivation_mode", enabled).apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false)
    }

    fun setLoggedIn(loggedIn: Boolean, email: String? = null) {
        prefs.edit().apply {
            putBoolean("is_logged_in", loggedIn)
            putString("user_email", email)
            apply()
        }
    }

    fun getUserEmail(): String? {
        return prefs.getString("user_email", "")
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun isPremiumUnlocked(): Boolean {
        return prefs.getBoolean("premium_unlocked", false)
    }

    fun setPremiumUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean("premium_unlocked", unlocked).apply()
    }

    fun useMetricSettings(): Boolean {
        return prefs.getBoolean("use_metric", true)
    }

    fun setUseMetricSettings(metric: Boolean) {
        prefs.edit().putBoolean("use_metric", metric).apply()
    }

    fun getThemeSelector(): String {
        return prefs.getString("theme_selector", "system") ?: "system"
    }

    fun setThemeSelector(theme: String) {
        prefs.edit().putString("theme_selector", theme).apply()
    }

    fun getDefaultGlassMl(): Int {
        return prefs.getInt("default_glass_ml", 250).coerceIn(100, 1000)
    }

    fun setDefaultGlassMl(value: Int) {
        prefs.edit().putInt("default_glass_ml", value.coerceIn(100, 1000)).apply()
    }

    fun getHybridPlanSettings(): HybridPlanSettings {
        return HybridPlanSettings(
            strengthDaysPerWeek = prefs.getInt("hybrid_strength_days", 3).coerceIn(0, 7),
            cardioDaysPerWeek = prefs.getInt("hybrid_cardio_days", 2).coerceIn(0, 7),
            objective = prefs.getString("hybrid_objective", "Recomposicion") ?: "Recomposicion",
            level = prefs.getString("hybrid_level", "Principiante") ?: "Principiante",
            sessionMinutes = prefs.getInt("hybrid_session_minutes", 45).coerceIn(10, 120),
            runningEnabled = prefs.getBoolean("hybrid_running_enabled", true),
            walkingEnabled = prefs.getBoolean("hybrid_walking_enabled", true),
            cyclingEnabled = prefs.getBoolean("hybrid_cycling_enabled", true)
        )
    }

    fun setHybridPlanSettings(settings: HybridPlanSettings) {
        prefs.edit().apply {
            putInt("hybrid_strength_days", settings.strengthDaysPerWeek.coerceIn(0, 7))
            putInt("hybrid_cardio_days", settings.cardioDaysPerWeek.coerceIn(0, 7))
            putString("hybrid_objective", settings.objective)
            putString("hybrid_level", settings.level)
            putInt("hybrid_session_minutes", settings.sessionMinutes.coerceIn(10, 120))
            putBoolean("hybrid_running_enabled", settings.runningEnabled)
            putBoolean("hybrid_walking_enabled", settings.walkingEnabled)
            putBoolean("hybrid_cycling_enabled", settings.cyclingEnabled)
            apply()
        }
    }

    fun clearAllPreferences() {
        prefs.edit().clear().apply()
    }
}
