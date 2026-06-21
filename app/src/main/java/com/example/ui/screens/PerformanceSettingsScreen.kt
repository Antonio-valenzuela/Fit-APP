package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DevicePerformanceUtils
import com.example.data.model.AppPerformanceMode
import com.example.ui.components.FeatureToggleItem
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsScreen(
    viewModel: FitPlanViewModel,
    onNavigate: (String) -> Unit
) {
    val preferences by viewModel.performancePreferences.collectAsStateWithLifecycle()
    val profile by viewModel.visualPerformanceProfile.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val context = LocalContext.current
    val detectedTierText = DevicePerformanceUtils.tierLabel(profile.deviceTier)

    Scaffold(
        containerColor = Color.Transparent,
        modifier = if (profile.gradientsEnabled) {
            Modifier.frostedMeshBackground(isDark)
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        },
        topBar = {
            TopAppBar(
                title = { Text("Modo rápido", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar a ajustes"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "FitPlan puede adaptar automaticamente el diseno segun tu dispositivo para mantener una experiencia fluida.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Dispositivo detectado: $detectedTierText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    PerformanceModeOption(
                        title = "Automatico segun dispositivo",
                        description = "FitPlan elige la experiencia recomendada para tu celular.",
                        icon = Icons.Default.Settings,
                        selected = preferences.selectedMode == AppPerformanceMode.AUTO,
                        onClick = { viewModel.updatePerformanceMode(AppPerformanceMode.AUTO) }
                    )
                    HorizontalDivider()
                    PerformanceModeOption(
                        title = "Mas visual",
                        description = "Usa más detalles visuales, animaciones y gráficas.",
                        icon = Icons.Default.AutoAwesome,
                        selected = preferences.selectedMode == AppPerformanceMode.VISUAL_HIGH,
                        onClick = { viewModel.updatePerformanceMode(AppPerformanceMode.VISUAL_HIGH) }
                    )
                    HorizontalDivider()
                    PerformanceModeOption(
                        title = "Equilibrado",
                        description = "Buen diseno con efectos moderados y buena fluidez.",
                        icon = Icons.Default.Speed,
                        selected = preferences.selectedMode == AppPerformanceMode.BALANCED,
                        onClick = { viewModel.updatePerformanceMode(AppPerformanceMode.BALANCED) }
                    )
                    HorizontalDivider()
                    PerformanceModeOption(
                        title = "Mas fluido",
                        description = "Prioriza estabilidad, bateria y rapidez con una interfaz simple.",
                        icon = Icons.Default.Bolt,
                        selected = preferences.selectedMode == AppPerformanceMode.LIGHTWEIGHT,
                        onClick = { viewModel.updatePerformanceMode(AppPerformanceMode.LIGHTWEIGHT) }
                    )
                }
            }

            if (preferences.selectedMode == AppPerformanceMode.VISUAL_HIGH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GoldenAmber.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "El modo visual premium puede consumir más batería o recursos en dispositivos modestos.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldenAmber
                    )
                }
            }

            Text(
                text = "Ajustes manuales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    FeatureToggleItem(
                        title = "Desactivar animaciones",
                        description = "Muestra y oculta contenido sin transiciones.",
                        icon = Icons.Default.VisibilityOff,
                        checked = preferences.animationsDisabled,
                        onCheckedChange = { viewModel.setPerformanceAnimationsDisabled(it) }
                    )
                    HorizontalDivider()
                    FeatureToggleItem(
                        title = "Reducir graficos",
                        description = "Usa resumen numérico cuando el celular necesita ir más ligero.",
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        checked = preferences.chartsDisabled,
                        onCheckedChange = { viewModel.setPerformanceChartsDisabled(it) }
                    )
                    HorizontalDivider()
                    FeatureToggleItem(
                        title = "Reducir efectos visuales",
                        description = "Quita blur, glassmorphism, sombras y gradientes complejos.",
                        icon = Icons.Default.AutoAwesome,
                        checked = preferences.visualEffectsDisabled,
                        onCheckedChange = { viewModel.setPerformanceVisualEffectsDisabled(it) }
                    )
                    HorizontalDivider()
                    FeatureToggleItem(
                        title = "Reducir widgets del inicio",
                        description = "Muestra menos tarjetas para abrir Inicio más rápido.",
                        icon = Icons.Default.Widgets,
                        checked = preferences.dashboardWidgetLimit != null,
                        onCheckedChange = { enabled ->
                            viewModel.setDashboardWidgetLimit(if (enabled) 5 else null)
                        }
                    )
                    HorizontalDivider()
                    FeatureToggleItem(
                        title = "Reducir historial mostrado",
                        description = "Muestra menos registros recientes para cargar más rápido.",
                        icon = Icons.Default.History,
                        checked = preferences.historyItemLimit != null,
                        onCheckedChange = { enabled ->
                            viewModel.setHistoryItemLimit(if (enabled) 10 else null)
                        }
                    )
                    HorizontalDivider()
                    FeatureToggleItem(
                        title = "Ahorro de bateria",
                        description = "Fuerza tarjetas ligeras, menos datos y cero efectos pesados.",
                        icon = Icons.Default.Bolt,
                        checked = preferences.batterySaverEnabled,
                        onCheckedChange = { viewModel.setBatterySaverEnabled(it) }
                    )
                }
            }

            PerformanceProfileSummary(profile = profile)

            Button(
                onClick = {
                    viewModel.resetPerformanceSettings()
                    Toast.makeText(
                        context,
                        "Configuracion automatica restaurada",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Restablecer configuracion automatica", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerformanceModeOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun PerformanceProfileSummary(profile: com.example.data.model.VisualPerformanceProfile) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Experiencia activa",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Inicio: hasta ${profile.maxDashboardWidgets} tarjetas visibles",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Animaciones: ${if (profile.animationsEnabled) "activas" else "reducidas"} | Graficas: ${if (profile.chartsEnabled) "visuales" else "resumen"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Tarjetas: ${if (profile.useLightweightCards) "ligeras" else "detalladas"} | Historial: ${profile.maxHistoryItems} registros",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

