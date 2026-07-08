package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.Composable
import com.example.ui.screens.LocalIsLowEnd

/**
 * Applies a premium mesh background with soft radial ambient gradients,
 * matching the HTML backdrop.
 */
@Composable
fun Modifier.frostedMeshBackground(isDark: Boolean): Modifier {
    val isLowEnd = LocalIsLowEnd.current
    return this.drawBehind {
        // Fill base background
        val baseColor = Color(0xFF0D0D0D)
        drawRect(color = baseColor)

        if (!isLowEnd) {
            // Top-left soft ambient fitness glow.
            val topLeftColor = Color(0x2200C27B)
            val topLeftBrush = Brush.radialGradient(
                colors = listOf(topLeftColor, Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.maxDimension * 0.75f
            )
            drawRect(brush = topLeftBrush)

            val bottomRightColor = Color(0x18009660)
            val bottomRightBrush = Brush.radialGradient(
                colors = listOf(bottomRightColor, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.maxDimension * 0.75f
            )
            drawRect(brush = bottomRightBrush)
        }
    }
}

/**
 * Conforms any container to a glass card with translucent color,
 * double gradient border highlighting, and soft ambient drop shadows.
 */
@Composable
fun Modifier.glassCard(
    cornerRadius: Dp = 24.dp,
    shadowElevation: Dp = 3.dp,
    isDark: Boolean = false
): Modifier {
    val isLowEnd = LocalIsLowEnd.current
    
    val shadowModifier = if (isLowEnd) this else this.shadow(
        elevation = shadowElevation,
        shape = RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFF0061A4).copy(alpha = 0.05f),
        spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFF0061A4).copy(alpha = 0.08f)
    )

    return shadowModifier
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            val cardColor = if (isLowEnd) Color(0xFF1A1A1A) else Color(0xE61A1A1A)
            drawRect(color = cardColor)
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x3300C27B),
                    Color(0x1AFFFFFF)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}
