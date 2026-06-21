package com.example

import android.os.Build
import android.util.DisplayMetrics
import com.example.data.local.DevicePerformanceUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePerformanceUtilsTest {
    @Test
    fun detectsLowEndDevice() {
        val tier = DevicePerformanceUtils.classifyDeviceTier(
            DevicePerformanceUtils.DevicePerformanceSignals(
                totalRamGb = 2.0,
                cpuCores = 4,
                sdkInt = Build.VERSION_CODES.P,
                widthPixels = 720,
                heightPixels = 1280,
                densityDpi = DisplayMetrics.DENSITY_XHIGH,
                isLowRamDevice = true
            )
        )

        assertEquals(DevicePerformanceUtils.DeviceTier.LOW_END, tier)
    }

    @Test
    fun detectsMidRangeDevice() {
        val tier = DevicePerformanceUtils.classifyDeviceTier(
            DevicePerformanceUtils.DevicePerformanceSignals(
                totalRamGb = 4.0,
                cpuCores = 6,
                sdkInt = Build.VERSION_CODES.R,
                widthPixels = 1080,
                heightPixels = 2400,
                densityDpi = DisplayMetrics.DENSITY_XXHIGH,
                isLowRamDevice = false
            )
        )

        assertEquals(DevicePerformanceUtils.DeviceTier.MID_RANGE, tier)
    }

    @Test
    fun detectsHighEndDevice() {
        val tier = DevicePerformanceUtils.classifyDeviceTier(
            DevicePerformanceUtils.DevicePerformanceSignals(
                totalRamGb = 8.0,
                cpuCores = 8,
                sdkInt = Build.VERSION_CODES.S,
                widthPixels = 1440,
                heightPixels = 3120,
                densityDpi = DisplayMetrics.DENSITY_XXXHIGH,
                isLowRamDevice = false
            )
        )

        assertEquals(DevicePerformanceUtils.DeviceTier.HIGH_END, tier)
    }
}
