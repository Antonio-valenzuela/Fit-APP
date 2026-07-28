package com.example

import android.content.Context
import com.example.data.local.PreferencesManager

internal fun provisionLocalTestSession(context: Context, email: String): String {
    val preferences = PreferencesManager(context)
    preferences.endSession()
    val ownerId = preferences.startLocalAccountSession(email, TEST_LOCAL_PIN)
    preferences.setOnboardingCompleted(true)
    context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("feature_nutrition_enabled", true)
        .putBoolean("feature_workout_enabled", true)
        .putBoolean("feature_animations_enabled", false)
        .commit()
    return ownerId
}

internal fun activeTestOwner(context: Context): String =
    checkNotNull(PreferencesManager(context).getActiveOwnerId()) {
        "A protected local test session must be active"
    }

internal const val TEST_PREFERENCES = "fitplan_mx_preferences"
private const val TEST_LOCAL_PIN = "731946"
