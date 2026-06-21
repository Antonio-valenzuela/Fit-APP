package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.VisualPerformanceProfile
import com.example.ui.theme.glassCard

@Composable
fun AdaptiveScreenContainer(
    profile: VisualPerformanceProfile,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val baseColor = if (isDark) Color(0xFF0D1520) else MaterialTheme.colorScheme.background
    val background = if (profile.gradientsEnabled) {
        modifier.then(Modifier)
    } else {
        modifier.background(baseColor)
    }

    Box(
        modifier = background,
        content = content
    )
}

@Composable
fun AdaptiveCard(
    profile: VisualPerformanceProfile,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = com.example.ui.screens.LocalThemeIsDark.current
    val shape = RoundedCornerShape(if (profile.useLightweightCards) 12.dp else 16.dp)
    val elevation = if (profile.shadowsEnabled && !profile.useLightweightCards) 8.dp else 0.dp
    val containerColor = when {
        profile.glassmorphismEnabled -> Color.Transparent
        isDark -> Color(0xFF1E293B)
        else -> MaterialTheme.colorScheme.surface
    }

    val cardModifier = if (profile.glassmorphismEnabled) {
        modifier.glassCard(
            cornerRadius = 16.dp,
            shadowElevation = elevation,
            isDark = isDark
        )
    } else {
        modifier
    }

    Card(
        modifier = cardModifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (profile.useLightweightCards) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(if (profile.useLightweightCards) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (profile.useLightweightCards) 8.dp else 10.dp),
            content = content
        )
    }
}

@Composable
fun AdaptiveButton(
    text: String,
    profile: VisualPerformanceProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    outlined: Boolean = false
) {
    val contentPadding = if (profile.useLightweightCards) {
        PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    } else {
        PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = contentPadding,
            shape = RoundedCornerShape(10.dp)
        ) {
            AdaptiveButtonContent(text = text, icon = icon)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = contentPadding,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            AdaptiveButtonContent(text = text, icon = icon)
        }
    }
}

@Composable
fun AdaptiveDashboardHeader(
    userName: String,
    goal: String,
    profile: VisualPerformanceProfile,
    modifier: Modifier = Modifier
) {
    val headerModifier = if (profile.gradientsEnabled) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (profile.useLightweightCards) 12.dp else 16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                    )
                )
            )
            .padding(16.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    Column(
        modifier = headerModifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Hola, $userName",
            style = if (profile.useLightweightCards) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Meta preventiva: $goal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AdaptiveProgressWidget(
    profile: VisualPerformanceProfile,
    points: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    AdaptiveChart(
        profile = profile,
        points = points,
        labels = labels,
        modifier = modifier
    )
}

@Composable
fun AdaptiveStreakWidget(
    profile: VisualPerformanceProfile,
    currentStreak: Int,
    bestStreak: Int,
    onActionClick: () -> Unit
) {
    AdaptiveCard(profile = profile) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (profile.useLightweightCards) 42.dp else 50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = "Racha activa",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = "$currentStreak días activos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mejor marca: $bestStreak días",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AdaptiveButton(
                text = "Registrar",
                profile = profile,
                onClick = onActionClick
            )
        }
    }
}

@Composable
fun AdaptiveMissionWidget(
    profile: VisualPerformanceProfile,
    title: String,
    completed: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (profile.useLightweightCards) 0.22f else 0.32f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (completed) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Assignment,
            contentDescription = if (completed) "Completa" else "Pendiente",
            tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun AdaptiveChart(
    profile: VisualPerformanceProfile,
    points: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    PerformanceAwareChart(
        points = points,
        labels = labels,
        chartsEnabled = profile.chartsEnabled,
        performanceModeEnabled = profile.useLightweightCards || !profile.advancedChartsEnabled,
        modifier = modifier
    )
}

@Composable
fun AdaptiveVisibility(
    visible: Boolean,
    profile: VisualPerformanceProfile,
    content: @Composable () -> Unit
) {
    if (profile.animationsEnabled) {
        AnimatedVisibility(visible = visible) {
            content()
        }
    } else if (visible) {
        content()
    }
}

@Composable
private fun AdaptiveButtonContent(
    text: String,
    icon: ImageVector?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

