package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.data.model.VisualPerformanceProfile
import com.example.ui.theme.glassCard

@Composable
fun OptionalFeatureWrapper(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (enabled) {
        content()
    }
}

@Composable
fun LightweightCard(
    modifier: Modifier = Modifier,
    performanceModeEnabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = com.example.ui.screens.LocalThemeIsDark.current
    val borderStroke = if (performanceModeEnabled) {
        BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x1F000000))
    } else null

    val cardModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    if (performanceModeEnabled) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
            ),
            border = borderStroke,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = cardModifier,
            content = content
        )
    } else {
        // High-end UI (using custom glassmorphism modifier if not under performance constraint)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = cardModifier.glassCard(cornerRadius = 16.dp, isDark = isDark),
            content = content
        )
    }
}

@Composable
fun FeatureToggleItem(
    title: String,
    description: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("toggle_${title.replace(" ", "_").lowercase()}")
        )
    }
}

@Composable
fun DashboardWidgetContainer(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    performanceModeEnabled: Boolean,
    modifier: Modifier = Modifier,
    visualProfile: VisualPerformanceProfile? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (visualProfile?.useLightweightCards == true) 0.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (actionText != null && onActionClick != null) {
                    TextButton(
                        onClick = onActionClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Real Content
            content()
        }
    }

    if (visualProfile != null) {
        AdaptiveCard(
            profile = visualProfile,
            modifier = modifier.fillMaxWidth(),
            content = cardContent
        )
    } else {
        LightweightCard(
            modifier = modifier.fillMaxWidth(),
            performanceModeEnabled = performanceModeEnabled
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (actionText != null && onActionClick != null) {
                        TextButton(
                            onClick = onActionClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        ) {
                            Text(
                                text = actionText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                content()
            }
        }
    }
}

@Composable
fun PerformanceAwareChart(
    points: List<Float>,
    labels: List<String>,
    chartsEnabled: Boolean,
    performanceModeEnabled: Boolean,
    modifier: Modifier = Modifier,
    yMax: Float = 100f
) {
    if (!chartsEnabled) {
        // Fallback: show simple data rows (extremely light, no graphic performance overhead)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Resumen de registros históricos (Modo de Ahorro Activo):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val chunked = labels.zip(points)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                chunked.takeLast(4).forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = label, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = String.format("%.1f", value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // Draw elegant canvas line chart
        val isDark = com.example.ui.screens.LocalThemeIsDark.current
        val primaryColor = MaterialTheme.colorScheme.primary
        val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

        Column(modifier = modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                if (points.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val pointCount = points.size
                val stepX = width / (if (pointCount > 1) (pointCount - 1) else 1)

                val minVal = points.minOrNull() ?: 0f
                val maxVal = points.maxOrNull() ?: yMax
                val diff = if (maxVal - minVal == 0f) 1f else (maxVal - minVal)

                val coordinates = points.mapIndexed { index, value ->
                    val x = index * stepX
                    val y = height - ((value - minVal) / diff) * height
                    androidx.compose.ui.geometry.Offset(x, y)
                }

                // If not in performance mode, we can draw a beautiful grid background
                if (!performanceModeEnabled) {
                    val gridLinesCount = 2
                    val gridStepY = height / gridLinesCount
                    for (i in 0..gridLinesCount) {
                        val ly = i * gridStepY
                        drawLine(
                            color = if (isDark) Color(0x1BFFFFFF) else Color(0x1A000000),
                            start = androidx.compose.ui.geometry.Offset(0f, ly),
                            end = androidx.compose.ui.geometry.Offset(width, ly),
                            strokeWidth = 1f
                        )
                    }
                }

                // Draw line connection (using continuous Path)
                val path = androidx.compose.ui.graphics.Path().apply {
                    coordinates.forEachIndexed { idx, offset ->
                        if (idx == 0) moveTo(offset.x, offset.y)
                        else lineTo(offset.x, offset.y)
                    }
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (performanceModeEnabled) 1.5.dp.toPx() else 3.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                if (!performanceModeEnabled && coordinates.size > 1) {
                    // Draw glowing under-gradient to give a premium glass design if not in performance mode
                    val fillPath = androidx.compose.ui.graphics.Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // Draw dots at data points
                    coordinates.forEach { offset ->
                        drawCircle(
                            color = accentColor,
                            radius = 3.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.5.dp.toPx(),
                            center = offset
                        )
                    }
                }
            }

            // Draw clean labels row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

