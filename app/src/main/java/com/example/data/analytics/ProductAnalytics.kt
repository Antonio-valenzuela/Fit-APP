package com.example.data.analytics

import android.util.Log

object ProductAnalytics {
    private const val TAG = "FitPlanAnalytics"

    fun track(event: String, properties: Map<String, String> = emptyMap()) {
        val payload = if (properties.isEmpty()) "" else properties.entries.joinToString(
            prefix = " {",
            postfix = "}"
        ) { "${it.key}=${it.value}" }
        Log.d(TAG, "$event$payload")
    }
}
