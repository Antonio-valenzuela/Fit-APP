package com.example.data.analytics

import android.util.Log
import com.example.BuildConfig

object ProductAnalytics {
    private const val TAG = "FitPlanAnalytics"
    private val allowedEvents = setOf(
        "onboarding_started",
        "paywall_seen",
        "premium_purchase_tapped"
    )

    @Suppress("UNUSED_PARAMETER")
    fun track(event: String, properties: Map<String, String> = emptyMap()) {
        if (!BuildConfig.DEBUG) return
        if (event !in allowedEvents) return
        Log.d(TAG, event)
    }
}
