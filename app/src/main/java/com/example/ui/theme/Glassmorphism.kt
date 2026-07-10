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
        val baseColor = if (isDark) Color(0xFF0B0C10) else Color(0xFFF8F9FD)
        drawRect(color = baseColor)

        if (!isLowEnd) {
            // Top-left soft ambient fitness glow.
            val topLeftColor = if (isDark) Color(0x1B6C5DD3) else Color(0x154361EE)
            val topLeftBrush = Brush.radialGradient(
                colors = listOf(topLeftColor, Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.maxDimension * 0.85f
            )
            drawRect(brush = topLeftBrush)

            val bottomRightColor = if (isDark) Color(0x124CC9F0) else Color(0x104CC9F0)
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
        ambientColor = if (isDark) Color.Black.copy(alpha = 0.3f) else Color(0xFF4361EE).copy(alpha = 0.04f),
        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFF4361EE).copy(alpha = 0.06f)
    )

    return shadowModifier
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            val cardColor = if (isDark) {
                if (isLowEnd) Color(0xFF161720) else Color(0xF2161720)
            } else {
                if (isLowEnd) Color(0xFFFFFFFF) else Color(0xF2FFFFFF)
            }
            drawRect(color = cardColor)
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0x2B6C5DD3),
                        Color(0x0DFFFFFF)
                    )
                } else {
                    listOf(
                        Color(0x3D4361EE),
                        Color(0x1F4361EE)
                    )
                }
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}
