package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FitPlanThemeColorTest {
    @Test
    fun usesDarkFitnessGreenPalette() {
        assertEquals(Color(0xFF00C27B), PrimaryDark)
        assertEquals(Color(0xFF00C27B), PrimaryLight)
        assertEquals(Color(0xFF0D0D0D), BackgroundDark)
        assertEquals(Color(0xFF0D0D0D), BackgroundLight)
        assertEquals(Color(0xFF1A1A1A), SurfaceDark)
        assertEquals(Color(0xFF1A1A1A), SurfaceLight)
    }
}
