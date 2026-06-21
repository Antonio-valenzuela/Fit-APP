package com.example.data.model

data class FeatureSettings(
    val nutritionEnabled: Boolean = true,
    val workoutEnabled: Boolean = true,
    val progressEnabled: Boolean = true,
    val healthEnabled: Boolean = true,
    val aiRecommendationsEnabled: Boolean = false,
    val premiumEnabled: Boolean = false,
    val chartsEnabled: Boolean = true,
    val remindersEnabled: Boolean = false,
    val animationsEnabled: Boolean = false,
    val performanceModeEnabled: Boolean = true,
    val streakEnabled: Boolean = true,
    val showStreakWidget: Boolean = true,
    val dailyMissionsEnabled: Boolean = true,
    val achievementsEnabled: Boolean = true,
    val motivationMessagesEnabled: Boolean = true,
    val softMotivationMode: Boolean = true
)
