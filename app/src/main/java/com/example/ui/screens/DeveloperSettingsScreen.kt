package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppPerformanceMode
import com.example.ui.theme.AlertRed
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    viewModel: FitPlanViewModel,
    onNavigate: (String) -> Unit
) {
    val settings by viewModel.featureSettings.collectAsStateWithLifecycle()
    val profile by viewModel.visualPerformanceProfile.collectAsStateWithLifecycle()
    val preferences by viewModel.performancePreferences.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        topBar = {
            TopAppBar(
                title = { Text("Developer settings", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                DevSection(title = "Estado interno") {
                    DevLine("nutritionEnabled", settings.nutritionEnabled.toString())
                    DevLine("workoutEnabled", settings.workoutEnabled.toString())
                    DevLine("progressEnabled", settings.progressEnabled.toString())
                    DevLine("healthEnabled", settings.healthEnabled.toString())
                    DevLine("aiRecommendationsEnabled", settings.aiRecommendationsEnabled.toString())
                    DevLine("chartsEnabled", settings.chartsEnabled.toString())
                    DevLine("remindersEnabled", settings.remindersEnabled.toString())
                    DevLine("streakEnabled", settings.streakEnabled.toString())
                    DevLine("dailyMissionsEnabled", settings.dailyMissionsEnabled.toString())
                    DevLine("achievementsEnabled", settings.achievementsEnabled.toString())
                }
            }

            item {
                DevSection(title = "Perfil visual") {
                    DevLine("selectedMode", preferences.selectedMode.name)
                    DevLine("deviceTier", profile.deviceTier.name)
                    DevLine("animationsEnabled", profile.animationsEnabled.toString())
                    DevLine("chartsEnabled", profile.chartsEnabled.toString())
                    DevLine("maxDashboardWidgets", profile.maxDashboardWidgets.toString())
                    DevLine("maxHistoryItems", profile.maxHistoryItems.toString())
                    DevLine("useLightweightCards", profile.useLightweightCards.toString())
                }
            }

            item {
                DevSection(title = "Acciones de prueba") {
                    Button(
                        onClick = {
                            viewModel.updatePerformanceMode(AppPerformanceMode.LIGHTWEIGHT)
                            Toast.makeText(context, "Forced lightweight profile", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Text("Forzar gama baja / ligero", modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = {
                            viewModel.updatePerformanceMode(AppPerformanceMode.VISUAL_HIGH)
                            Toast.makeText(context, "Forced high visual profile", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Text("Forzar gama alta / visual", modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = {
                            com.example.reminder.ReminderScheduler.scheduleDailyReminder(context)
                            viewModel.setRemindersEnabled(true)
                            Toast.makeText(context, "Reminder worker scheduled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Text("Programar worker de recordatorio", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetPerformanceSettings()
                            Toast.makeText(context, "Performance settings reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Reiniciar perfil de rendimiento", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.purgeAccountData()
                            Toast.makeText(context, "Local data cleared", Toast.LENGTH_LONG).show()
                            onNavigate(Destinations.LOGIN)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed)
                        Text(
                            "Limpiar base local",
                            color = AlertRed,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DevLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
