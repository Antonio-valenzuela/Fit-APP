package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AchievementEntity
import com.example.data.model.DailyMissionEntity
import com.example.ui.theme.GoldenAmber
import com.example.ui.viewmodel.AchievementViewModel
import com.example.ui.viewmodel.DailyMissionViewModel
import com.example.ui.viewmodel.FitPlanViewModel
import com.example.ui.viewmodel.StreakViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. DAILY CHECK-IN SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCheckInScreen(
    onNavigate: (String) -> Unit,
    fitPlanViewModel: FitPlanViewModel,
    streakViewModel: StreakViewModel = viewModel()
) {
    val uiState by streakViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Quick selections state
    var waterSelected by remember { mutableStateOf(false) }
    var foodSelected by remember { mutableStateOf(false) }
    var workoutSelected by remember { mutableStateOf(false) }
    var sleepSelected by remember { mutableStateOf(false) }
    var weightSelected by remember { mutableStateOf(false) }
    var energeticSelected by remember { mutableStateOf(false) }
    val hasSelectedAction = waterSelected || foodSelected || workoutSelected || sleepSelected || weightSelected || energeticSelected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-In Diario", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Autoevaluación de Bienestar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Selecciona las acciones saludables que realizaste hoy. Sin culpa ni presiones, solo constancia.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Strongly typed options list
                data class CheckInOptionItem(
                    val text: String,
                    val icon: ImageVector,
                    val isSelected: Boolean,
                    val onToggle: () -> Unit
                )

                val options = listOf(
                    CheckInOptionItem("Tomé agua adecuada", Icons.Default.LocalDrink, waterSelected) { waterSelected = !waterSelected },
                    CheckInOptionItem("Comí de forma saludable", Icons.Default.Restaurant, foodSelected) { foodSelected = !foodSelected },
                    CheckInOptionItem("Realicé entrenamiento físico", Icons.Default.FitnessCenter, workoutSelected) { workoutSelected = !workoutSelected },
                    CheckInOptionItem("Logré dormir bien", Icons.Default.Bedtime, sleepSelected) { sleepSelected = !sleepSelected },
                    CheckInOptionItem("Registré peso / progreso", Icons.Default.Scale, weightSelected) { weightSelected = !weightSelected },
                    CheckInOptionItem("¿Te sientes con energía?", Icons.Default.ElectricBolt, energeticSelected) { energeticSelected = !energeticSelected }
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(options) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (item.isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (item.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { item.onToggle() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.text,
                                    fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (item.isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { item.onToggle() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Buttons Block
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasSelectedAction) {
                            Toast.makeText(context, "Selecciona al menos una accion antes de guardar.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        fitPlanViewModel.markDailyCheckInActions(
                            waterSelected = waterSelected,
                            foodSelected = foodSelected,
                            workoutSelected = workoutSelected,
                            sleepSelected = sleepSelected,
                            weightSelected = weightSelected
                        )
                        streakViewModel.performDailyCheckIn()
                        Toast.makeText(context, "¡Check-in guardado! Tu constancia protege tu bienestar.", Toast.LENGTH_SHORT).show()
                        onNavigate(Destinations.DASHBOARD)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_checkin_button"),
                    enabled = hasSelectedAction,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar check-in", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onNavigate(Destinations.DASHBOARD) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Omitir por ahora")
                }
            }
        }
    }
}

// ==========================================
// 2. DAILY MISSIONS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMissionsScreen(
    onNavigate: (String) -> Unit,
    missionsViewModel: DailyMissionViewModel = viewModel()
) {
    val missions by missionsViewModel.missions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Misiones de Hoy", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Misiones de Enfoque Preventivo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Completa diariamente estas misiones rápidas. Fomentan pequeños hábitos sostenibles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (missions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aun no hay misiones listas para hoy. Vuelve a Inicio y registra una accion saludable para generar nuevas misiones.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(missions) { mission ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (mission.completed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mission.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textDecoration = if (mission.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            color = if (mission.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mission.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            missionsViewModel.forceToggleMission(mission)
                                            if (!mission.completed) {
                                                Toast.makeText(context, "¡Misión completada! Qué gran avance.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (mission.completed) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (mission.completed) {
                                            Icon(Icons.Default.Check, contentDescription = "Completada", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("Completar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    missionsViewModel.loadMissions()
                    onNavigate(Destinations.DASHBOARD)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Listo", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 3. ACHIEVEMENTS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigate: (String) -> Unit,
    achievementViewModel: AchievementViewModel = viewModel()
) {
    val unlockedList by achievementViewModel.achievements.collectAsStateWithLifecycle()

    // Fixed pool of all potential badges/achievements
    val allAchievementsPool = listOf(
        Pair("ach_first_checkin", Pair("Primer Check-in", "Completaste tu primer registro de bienestar diario")),
        Pair("ach_first_food", Pair("Alimentación Consciente", "Comenzaste a registrar tu dieta diaria para control metabólico")),
        Pair("ach_first_workout", Pair("Activación Física", "Iniciaste el camino activo con tu primer entrenamiento preventivo")),
        Pair("ach_streak_3", Pair("Enfoque Continuo", "Mantuviste tu racha saludable por 3 días consecutivos")),
        Pair("ach_streak_7", Pair("Hábito Formado", "Lograste una racha perfecta de 7 días continuos")),
        Pair("ach_streak_14", Pair("Disciplina de Acero", "Completaste 14 días conservando tu salud y constancia")),
        Pair("ach_streak_30", Pair("Estilo de Vida Saludable", "Alcanzaste una racha legendaria de 30 días")),
        Pair("ach_active_week", Pair("Semana Imparable", "Registraste actividades saludables todos los días de la semana activa")),
        Pair("ach_all_missions", Pair("Eficiencia Absoluta", "Completaste con éxito todas tus 3 misiones diarias")),
        Pair("ach_hydration", Pair("Tomo Agua Constante", "Mantuviste tres días de hidratación óptima registrada")),
        Pair("ach_epic_return", Pair("Regreso Heroico", "Superaste un bache y regresaste fuerte para retomar la racha"))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insignias y Logros", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Vitrina de Logros FitPlan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Cada medalla representa perseverancia y compromiso continuo con tu estado físico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (unlockedList.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Empieza con tu primera accion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Haz un check-in, registra agua, comida o entrenamiento para desbloquear tus primeras insignias.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { onNavigate(Destinations.DAILY_CHECK_IN) }) {
                                Text("Hacer check-in")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(allAchievementsPool) { (key, pair) ->
                        val unlockedInfo = unlockedList.firstOrNull { it.achievementKey == key }
                        val isUnlocked = unlockedInfo != null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                                // horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isUnlocked) goldenAmberGradientBrush()
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isUnlocked) Icons.Default.MilitaryTech else Icons.Default.Lock,
                                        contentDescription = if (isUnlocked) "Insignia desbloqueada" else "Insignia bloqueada",
                                        tint = if (isUnlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pair.first,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pair.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (unlockedInfo != null) {
                                        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(unlockedInfo.unlockedAt))
                                        Text(
                                            text = "Desbloqueado el $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onNavigate(Destinations.DASHBOARD) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cerrar vitrina", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Golden Amber gradient function for outstanding design matching frontend-design skills
@Composable
fun goldenAmberGradientBrush(): Color {
    return GoldenAmber
}

