package com.example.ui.muscles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.data.model.BodyModel
import com.example.data.model.Muscle
import com.example.data.model.MuscleImbalance
import com.example.data.model.MuscleIntensity
import com.example.data.model.UserDiscipline
import com.example.data.model.intensityToColor
import com.example.ui.screens.Destinations
import com.example.ui.screens.FitPlanBottomBar
import com.example.ui.screens.LocalThemeIsDark
import com.example.ui.theme.frostedMeshBackground

object MuscleMapNavigationDefaults {
    const val selectedRoute: String = Destinations.MUSCLE_MAP
    val defaultDiscipline: UserDiscipline = UserDiscipline.GYM
}

@Composable
fun MuscleMapScreen(
    onNavigate: (String) -> Unit,
    isPremium: Boolean,
    discipline: UserDiscipline = MuscleMapNavigationDefaults.defaultDiscipline,
    viewModel: MuscleMapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val selectedInfo = state.selectedMuscle?.let { state.heatmap[it] }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        bottomBar = {
            FitPlanBottomBar(
                selectedRoute = MuscleMapNavigationDefaults.selectedRoute,
                onNavigate = onNavigate,
                isPremium = isPremium,
                discipline = discipline
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigate(Destinations.PROGRESS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Mapa muscular",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Balance de entrenamiento segun tus registros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                RecommendationPanel(
                    muscle = state.recommendation?.muscle,
                    reason = state.recommendation?.reason,
                    urgency = state.recommendation?.urgency,
                    onTrain = { onNavigate(Destinations.WORKOUT) }
                )
            }

            item {
                BodyModelToggle(
                    selected = state.selectedBodyModel,
                    onSelect = viewModel::selectBodyModel
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BodyHeatMapCanvas(
                            bodyModel = state.selectedBodyModel,
                            heatmap = state.heatmap,
                            selected = state.selectedMuscle,
                            onSelect = viewModel::selectMuscle
                        )
                        IntensityLegend()
                    }
                }
            }

            if (!state.hasWorkoutData) {
                item {
                    EmptyMuscleDataCard(onTrain = { onNavigate(Destinations.WORKOUT) })
                }
            }

            item {
                MuscleDetailCard(
                    selectedMuscle = state.selectedMuscle,
                    info = selectedInfo
                )
            }

            if (state.heatmap.isNotEmpty()) {
                item {
                    Text(
                        "Trabajo reciente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.heatmap.values.sortedByDescending { it.intensity }.take(8)) { item ->
                    MuscleIntensityRow(item)
                }
            }

            if (state.imbalances.isNotEmpty()) {
                item {
                    Text(
                        "Desbalances detectados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.imbalances) { imbalance ->
                    ImbalanceCard(imbalance)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RecommendationPanel(
    muscle: Muscle?,
    reason: String?,
    urgency: String?,
    onTrain: () -> Unit
) {
    val accent = when (urgency) {
        "high" -> Color(0xFFE11D48)
        "medium" -> Color(0xFFF97316)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent)
                )
                Text(
                    text = "Recomendacion de balance",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = muscle?.let { "Entrena: ${it.displayName}" } ?: "Registra un entrenamiento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = reason ?: "FitPlan necesita tus primeros registros para calcular el mapa muscular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onTrain, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Ir a entrenar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BodyModelToggle(selected: BodyModel, onSelect: (BodyModel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == BodyModel.MALE,
            onClick = { onSelect(BodyModel.MALE) },
            label = { Text("Modelo masculino") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selected == BodyModel.FEMALE,
            onClick = { onSelect(BodyModel.FEMALE) },
            label = { Text("Modelo femenino") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IntensityLegend() {
    val steps = listOf(
        0 to "Sin datos",
        25 to "Baja",
        50 to "Media",
        75 to "Alta",
        100 to "Max"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { (intensity, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(intensityToColor(intensity)))
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyMuscleDataCard(onTrain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Aun no hay datos musculares", fontWeight = FontWeight.Bold)
            Text(
                "Guarda una rutina y FitPlan estimara que grupos trabajaste para mostrar balance, intensidad y recomendaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onTrain, modifier = Modifier.fillMaxWidth()) {
                Text("Registrar entrenamiento")
            }
        }
    }
}

@Composable
private fun MuscleDetailCard(selectedMuscle: Muscle?, info: MuscleIntensity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                selectedMuscle?.displayName ?: "Toca un musculo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (info == null) {
                Text(
                    "Selecciona una zona del cuerpo para ver intensidad, sesiones y ultimo trabajo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBlock("Intensidad", "${info.intensity}%")
                    MetricBlock("Sesiones", "${info.sessions}")
                    MetricBlock("Ultimo", info.lastTrained ?: "Sin dato")
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MuscleIntensityRow(item: MuscleIntensity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.muscleGroup.displayName, fontWeight = FontWeight.SemiBold)
                Text("${item.intensity}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { item.intensity / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(intensityToColor(item.intensity)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ImbalanceCard(imbalance: MuscleImbalance) {
    val high = imbalance.severity == "high"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (high) Color(0xFFFFE4E6) else Color(0xFFFFF7ED)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(imbalance.pair, fontWeight = FontWeight.Bold)
                Text(
                    if (high) "Prioridad alta" else "Atencion",
                    color = if (high) Color(0xFFBE123C) else Color(0xFFC2410C),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
            Text(
                "Menos trabajado: ${imbalance.weakMuscle.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                imbalance.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF334155)
            )
        }
    }
}
