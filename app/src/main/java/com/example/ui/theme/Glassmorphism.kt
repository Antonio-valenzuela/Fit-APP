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
        val baseColor = if (isDark) Color(0xFF0D1520) else Color(0xFFF8FAFC)
        drawRect(color = baseColor)

        if (!isLowEnd) {
            // Top-left soft ambient blue glow
            val topLeftColor = if (isDark) Color(0x1a0061A4) else Color(0x0c0061A4)
            val topLeftBrush = Brush.radialGradient(
                colors = listOf(topLeftColor, Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.maxDimension * 0.75f
            )
            drawRect(brush = topLeftBrush)

            // Bottom-right soft healthcare emerald glow
            val bottomRightColor = if (isDark) Color(0x1a006C4C) else Color(0x0a006C4C)
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
            val cardColor = if (isLowEnd) {
                if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
            } else {
                if (isDark) Color(0x7F182235) else Color(0xB1FFFFFF) // Opacity controlled
            }
            drawRect(color = cardColor)
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0x2BFFFFFF), // Transparent highlight
                        Color(0x05FFFFFF)  // Secondary subtle side
                    )
                } else {
                    listOf(
                        Color(0x60FFFFFF), // Lucid high opacity white highlight
                        Color(0x10FFFFFF)  // Faded secondary side
                    )
                }
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}
