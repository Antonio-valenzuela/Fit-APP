package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// RESPONSIVE BREAKPOINTS (ENTERPRISE GRID)
// ==========================================
enum class ScreenType {
    Compact, // Small phones (e.g. iPhone SE, old Androids) <360dp
    Mobile,  // Standard phones 360dp - 599dp
    Tablet,  // Foldables & Tablets 600dp - 1023dp
    Expanded // Large tablets & widescreen >1024dp
}

data class ScreenLayoutInfo(
    val screenType: ScreenType,
    val widthDp: Dp,
    val heightDp: Dp,
    val isTabletOrWider: Boolean,
    val gridColumns: Int,
    val standardSpacing: Dp,
    val cardCornerRadius: Dp
)

val LocalScreenInfo = compositionLocalOf<ScreenLayoutInfo> {
    error("No ScreenLayoutInfo provided")
}

val LocalIsLowEnd = compositionLocalOf<Boolean> {
    false
}

val LocalThemeIsDark = compositionLocalOf<Boolean> {
    false
}

/**
 * ResponsiveBuilder listens to the dynamic width and height constraints
 * of the parent container to calculate appropriate scaling, paddings, and styles.
 */
@Composable
fun ResponsiveBuilder(
    content: @Composable (ScreenLayoutInfo) -> Unit
) {
    BoxWithConstraints {
        val width = maxWidth
        val height = maxHeight

        val type = when {
            width < 360.dp -> ScreenType.Compact
            width < 600.dp -> ScreenType.Mobile
            width < 1024.dp -> ScreenType.Tablet
            else -> ScreenType.Expanded
        }

        val isTablet = type == ScreenType.Tablet || type == ScreenType.Expanded

        val columns = when (type) {
            ScreenType.Compact -> 1
            ScreenType.Mobile -> 1
            ScreenType.Tablet -> 2
            ScreenType.Expanded -> 3
        }

        val spacing = when (type) {
            ScreenType.Compact -> 12.dp
            ScreenType.Mobile -> 16.dp
            ScreenType.Tablet -> 24.dp
            ScreenType.Expanded -> 32.dp
        }

        val cornerRadius = when (type) {
            ScreenType.Compact -> 16.dp
            ScreenType.Mobile -> 20.dp
            ScreenType.Tablet, ScreenType.Expanded -> 24.dp
        }

        val info = ScreenLayoutInfo(
            screenType = type,
            widthDp = width,
            heightDp = height,
            isTabletOrWider = isTablet,
            gridColumns = columns,
            standardSpacing = spacing,
            cardCornerRadius = cornerRadius
        )

        androidx.compose.runtime.CompositionLocalProvider(LocalScreenInfo provides info) {
            content(info)
        }
    }
}

// ==========================================
// DYNAMIC TYPOGRAPHY SCALING BY SCREEN TYPE
// ==========================================
object DynamicTypography {
    val ScreenLayoutInfo.headlineNormal: TextUnit
        get() = when (screenType) {
            ScreenType.Compact -> 20.sp
            ScreenType.Mobile -> 24.sp
            ScreenType.Tablet -> 28.sp
            ScreenType.Expanded -> 32.sp
        }

    val ScreenLayoutInfo.bodyNormal: TextUnit
        get() = when (screenType) {
            ScreenType.Compact -> 13.sp
            ScreenType.Mobile -> 15.sp
            ScreenType.Tablet -> 16.sp
            ScreenType.Expanded -> 18.sp
        }

    val ScreenLayoutInfo.labelNormal: TextUnit
        get() = when (screenType) {
            ScreenType.Compact -> 11.sp
            ScreenType.Mobile -> 12.sp
            ScreenType.Tablet -> 13.sp
            ScreenType.Expanded -> 14.sp
        }
}

// ==========================================
// LOW-END HARDWARE ENGINE (RAM & HW ACCEL)
// ==========================================
object HardwareOptimizer {
    
    /**
     * Determines if the host device has hardware constraints (less than 4GB total RAM or Go edition)
     * which demands disabling glassmorphism gradients or background blur for optimal rendering times.
     */
    fun isLowEndDevice(context: Context): Boolean {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                
                // Devices with < 3.5GB RAM are treated as Low-End or Mid-Low to ensure smooth 60fps
                val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024f)
                totalRamGb < 3.5f || actManager.isLowRamDevice
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
