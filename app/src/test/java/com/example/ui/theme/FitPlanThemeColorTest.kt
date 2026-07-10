package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FitPlanThemeColorTest {
    @Test
    fun usesApprovedIndigoPaletteForLightAndDarkThemes() {
        assertEquals(Color(0xFF6C5DD3), PrimaryDark)
        assertEquals(Color(0xFF4361EE), PrimaryLight)
        assertEquals(Color(0xFF0B0C10), BackgroundDark)
        assertEquals(Color(0xFFF8F9FD), BackgroundLight)
        assertEquals(Color(0xFF161720), SurfaceDark)
        assertEquals(Color(0xFFFFFFFF), SurfaceLight)
    }
}
