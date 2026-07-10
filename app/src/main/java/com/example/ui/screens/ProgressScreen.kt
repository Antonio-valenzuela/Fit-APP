package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.data.model.UserDiscipline
import com.example.ui.muscles.MuscleMapViewModel
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel

@Composable
fun ProgressScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val items by viewModel.allHealthLogs.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val discipline = remember(user?.trainingDiscipline) { UserDiscipline.fromStoredName(user?.trainingDiscipline) }
    val muscleViewModel: MuscleMapViewModel = hiltViewModel()
    val muscleState by muscleViewModel.state.collectAsStateWithLifecycle()

    var activeTrendTab by remember { mutableStateOf("Peso") }

    val isDark = LocalThemeIsDark.current

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        bottomBar = { FitPlanBottomBar(Destinations.PROGRESS, onNavigate, isPremium, discipline) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Mi progreso",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                val severity = muscleState.imbalances.maxByOrNull { if (it.severity == "high") 2 else 1 }?.severity
                val balanceLabel = when (severity) {
                    "high" -> "Desbalance alto"
                    "medium" -> "Atencion"
                    else -> if (muscleState.hasWorkoutData) "Equilibrado" else "Sin balance todavia"
                }
                val recommended = muscleState.recommendation?.muscle?.displayName ?: "rutina inicial"
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Balance muscular",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$balanceLabel - recomendado: $recommended",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        muscleState.recommendation?.reason ?: "Completa al menos un entrenamiento para generar tu balance muscular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onNavigate(Destinations.MUSCLE_MAP) }, modifier = Modifier.weight(1f)) {
                            Text("Ver balance")
                        }
                        OutlinedButton(onClick = { onNavigate(Destinations.WORKOUT) }, modifier = Modifier.weight(1f)) {
                            Text("Entrenar")
                        }
                    }
                }
            }

            // Select active metrics trends
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Peso", "Energia", "Pasos", "Sueno").forEach { tab ->
                    FilterChip(
                        selected = activeTrendTab == tab,
                        onClick = { activeTrendTab = tab },
                        label = {
                            Text(
                                tab,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }
            }

            // FILTER RELEVANT DATAS TO PLOT
            val plotPoints = remember(items, activeTrendTab) {
                when (activeTrendTab) {
                    "Peso" -> items.filter { it.weightKg > 0 }.map { it.weightKg }
                    "Energia" -> items.filter { it.energyLevel > 0 }.map { it.energyLevel.toFloat() }
                    "Pasos" -> items.filter { it.steps > 0 }.map { it.steps.toFloat() }
                    else -> items.filter { it.sleepDurationHours > 0 }.map { it.sleepDurationHours }
                }.take(7).reversed()
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "$activeTrendTab - últimos 7 registros",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (plotPoints.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Registra al menos 2 datos para ver tu tendencia. Puedes empezar con peso, agua, glucosa, presión o sueño.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onNavigate(Destinations.HEALTH)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Registrar datos de salud",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // CUSTOM HIGH-PERFORMANCE GRADIENT LINE CANVAS PLOTTER
                        val strokeColor = MaterialTheme.colorScheme.primary
                        val glowColor = strokeColor.copy(alpha = 0.2f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val maxVal = (plotPoints.maxOrNull() ?: 100f) * 1.1f
                            val minVal = (plotPoints.minOrNull() ?: 0f) * 0.9f
                            val delta = (maxVal - minVal).coerceAtLeast(1f)

                            val path = Path()
                            val fillPath = Path()

                            val width = size.width
                            val height = size.height

                            val xInterval = width / (plotPoints.size - 1)

                            plotPoints.forEachIndexed { index, value ->
                                val x = index * xInterval
                                val normalizedY = ((value - minVal) / delta)
                                val y = height - (normalizedY * height)

                                if (index == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, height)
                                    fillPath.lineTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }

                                if (index == plotPoints.size - 1) {
                                    fillPath.lineTo(x, height)
                                    fillPath.close()
                                }

                                // Draw bullet points
                                drawCircle(
                                    color = strokeColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }

                            // Render main curve
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // Render nice translucent fill gradient
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(glowColor, Color.Transparent)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mín: ${plotPoints.minOrNull()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Máx: ${plotPoints.maxOrNull()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CLINCAL ADVICE CAP
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Como interpretar tu progreso",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelNormal()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Usa estas gráficas como seguimiento personal, no como diagnóstico médico. Si notas cambios fuertes o síntomas, consulta con un profesional de salud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

