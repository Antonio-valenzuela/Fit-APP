package com.example.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics

object DevicePerformanceUtils {
    enum class DeviceTier {
        LOW_END,
        MID_RANGE,
        HIGH_END
    }

    data class DevicePerformanceSignals(
        val totalRamGb: Double,
        val cpuCores: Int,
        val sdkInt: Int,
        val widthPixels: Int,
        val heightPixels: Int,
        val densityDpi: Int,
        val isLowRamDevice: Boolean
    ) {
        val screenMegapixels: Double
            get() = (widthPixels.toDouble() * heightPixels.toDouble()) / 1_000_000.0
    }

    fun detectDeviceTier(context: Context): DeviceTier {
        return classifyDeviceTier(readDevicePerformanceSignals(context))
    }

    fun isLowEndDevice(context: Context): Boolean {
        return detectDeviceTier(context) == DeviceTier.LOW_END
    }

    fun isMidRangeDevice(context: Context): Boolean {
        return detectDeviceTier(context) == DeviceTier.MID_RANGE
    }

    fun isHighEndDevice(context: Context): Boolean {
        return detectDeviceTier(context) == DeviceTier.HIGH_END
    }

    fun tierLabel(tier: DeviceTier): String {
        return when (tier) {
            DeviceTier.LOW_END -> "gama baja"
            DeviceTier.MID_RANGE -> "gama media"
            DeviceTier.HIGH_END -> "gama alta"
        }
    }

    fun classifyDeviceTier(signals: DevicePerformanceSignals): DeviceTier {
        if (signals.isLowRamDevice || signals.totalRamGb < 3.0 || signals.sdkInt < Build.VERSION_CODES.Q) {
            return DeviceTier.LOW_END
        }

        var score = 0

        score += when {
            signals.totalRamGb >= 8.0 -> 3
            signals.totalRamGb >= 5.0 -> 2
            signals.totalRamGb >= 3.0 -> 1
            else -> -2
        }

        score += when {
            signals.cpuCores >= 8 -> 2
            signals.cpuCores >= 6 -> 1
            signals.cpuCores <= 4 -> -1
            else -> 0
        }

        score += when {
            signals.sdkInt >= Build.VERSION_CODES.S -> 1
            signals.sdkInt < Build.VERSION_CODES.R -> -1
            else -> 0
        }

        if (signals.screenMegapixels > 3.5 && signals.totalRamGb < 4.0) {
            score -= 1
        }

        if (signals.densityDpi >= DisplayMetrics.DENSITY_XXHIGH && signals.totalRamGb < 4.0) {
            score -= 1
        }

        return when {
            score >= 5 -> DeviceTier.HIGH_END
            score <= 1 -> DeviceTier.LOW_END
            else -> DeviceTier.MID_RANGE
        }
    }

    private fun readDevicePerformanceSignals(context: Context): DevicePerformanceSignals {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val totalMemGb = if (memoryInfo.totalMem > 0L) {
                memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            } else {
                4.0
            }
            val metrics = context.resources.displayMetrics

            DevicePerformanceSignals(
                totalRamGb = totalMemGb,
                cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                sdkInt = Build.VERSION.SDK_INT,
                widthPixels = metrics.widthPixels,
                heightPixels = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                isLowRamDevice = activityManager?.isLowRamDevice == true
            )
        } catch (e: Exception) {
            val metrics = context.resources.displayMetrics
            DevicePerformanceSignals(
                totalRamGb = 4.0,
                cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                sdkInt = Build.VERSION.SDK_INT,
                widthPixels = metrics.widthPixels,
                heightPixels = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                isLowRamDevice = false
            )
        }
    }
}
