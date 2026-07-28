package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.ActivityTrackingRules
import com.example.data.model.GpsRoutePoint
import com.example.data.model.ActivitySessionSummaryRules
import com.example.data.model.UserDiscipline
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.ActivityTrackerViewModel
import com.example.ui.viewmodel.FitPlanViewModel
import com.example.ui.viewmodel.TrackerStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RunLogScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActiveActivityTrackerScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.RUNNING,
        onNavigate = onNavigate
    )
}

@Composable
fun RideLogScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActiveActivityTrackerScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.CYCLING,
        onNavigate = onNavigate
    )
}

@Composable
fun WalkLogScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActiveActivityTrackerScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.WALKING,
        onNavigate = onNavigate
    )
}

@Composable
fun WellnessDiaryScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val settings by viewModel.featureSettings.collectAsStateWithLifecycle()
    if (settings.healthEnabled) {
        HealthScreen(viewModel = viewModel, onNavigate = onNavigate)
    } else {
        FeatureBlockedScreen(
            title = "Diario de bienestar desactivado",
            description = "Activa la funcion de salud para registrar agua, sueno, energia, peso y check-ins.",
            onSettings = { onNavigate(Destinations.SETTINGS) },
            onBack = { onNavigate(Destinations.DASHBOARD) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveActivityTrackerScreen(
    viewModel: FitPlanViewModel,
    discipline: UserDiscipline,
    onNavigate: (String) -> Unit,
    trackerViewModel: ActivityTrackerViewModel = viewModel(key = "tracker_${discipline.name}")
) {
    val context = LocalContext.current
    val trackerState by trackerViewModel.state.collectAsStateWithLifecycle()
    val sessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val disciplineSessions = remember(sessions, discipline) {
        sessions.filter { it.discipline == discipline.name }
    }
    var caloriesText by remember { mutableStateOf("") }
    val hasFineLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val hasCoarseLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val hasLocationPermission by remember {
        derivedStateOf { hasFineLocationPermission.value || hasCoarseLocationPermission.value }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasFineLocationPermission.value = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasCoarseLocationPermission.value = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        trackerViewModel.prepare(discipline)
    }
    val isRun = discipline == UserDiscipline.RUNNING
    val isWalk = discipline == UserDiscipline.WALKING
    val title = if (isRun) "Iniciar carrera" else if (isWalk) "Iniciar caminata" else "Iniciar rodada"
    val startLabel = if (isRun) "Iniciar carrera" else if (isWalk) "Iniciar caminata" else "Iniciar rodada"
    val historyRoute = if (isRun) Destinations.RUNNING_HISTORY else if (isWalk) Destinations.WALKING_HISTORY else Destinations.CYCLING_HISTORY
    val icon = if (isRun) Icons.AutoMirrored.Filled.DirectionsRun else if (isWalk) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.AutoMirrored.Filled.DirectionsBike
    val preparationTitle = if (isRun) "Prepara tu carrera" else if (isWalk) "Preparación para tu caminata" else "Prepara tu rodada"
    val preparationDescription = if (isRun) {
        "Registra tiempo, distancia y ritmo para medir tu progreso."
    } else if (isWalk) {
        "Empieza con un paso firme y cómodo."
    } else {
        "Registra tiempo, distancia y ritmo para seguir tu avance."
    }
    val preparationBenefit = if (isRun) {
        "Ideal para mejorar resistencia y condición."
    } else if (isWalk) {
        "Ideal para mantener salud cardiovascular y movilidad."
    } else {
        "Ideal para mejorar resistencia sin impacto."
    }
    val preparationSuggestion = if (isRun) {
        "Empieza con 15-30 min a ritmo cómodo."
    } else if (isWalk) {
        "Camina de 20 a 45 min a un paso constante."
    } else {
        "Empieza con 20-40 min a intensidad moderada."
    }
    val canSave = ActivityTrackingRules.canSave(trackerState.distanceKm, trackerState.elapsedSeconds)

    DisposableEffect(discipline) {
        trackerViewModel.prepare(discipline)
        onDispose { }
    }

    Scaffold(
        containerColor = Color(0xFF07111F),
        modifier = Modifier.testTag("active_activity_${discipline.name.lowercase()}_screen"),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { onNavigate(historyRoute) }) {
                        Text("Ver historial", color = Color.White)
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
                .background(Color(0xFF07111F)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            GpsMapPanel(
                points = trackerState.points,
                currentLocation = trackerState.currentLocation,
                discipline = discipline,
                message = trackerState.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF7CFF4F), modifier = Modifier.size(26.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                trackerStatusLabel(trackerState.status),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("tracker_status_${trackerState.status.name.lowercase()}")
                            )
                            Text(trackerState.message, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (trackerState.status == TrackerStatus.READY) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(preparationTitle, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(preparationDescription, color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.bodySmall)
                                Text(preparationBenefit, color = Color(0xFF7CFF4F), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(preparationSuggestion, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DarkMetric("Tiempo", formatDuration(trackerState.elapsedSeconds), Modifier.weight(1f))
                        DarkMetric("Distancia", String.format(Locale.US, "%.2f km", trackerState.distanceKm), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DarkMetric(
                            if (isRun) "Ritmo" else "Vel. prom.",
                            if (isRun) {
                                formatPace(trackerState.averagePaceMinPerKm, pendingLabel = "Calculando...")
                            } else {
                                String.format(Locale.US, "%.1f km/h", trackerState.averageSpeedKmh)
                            },
                            Modifier.weight(1f)
                        )
                        DarkMetric(
                            if (isRun) "Puntos GPS" else "Vel. actual",
                            if (isRun) trackerState.validGpsPoints.toString() else String.format(Locale.US, "%.1f km/h", trackerState.currentSpeedKmh),
                            Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Precision: ${formatGpsAccuracy(trackerState.lastGpsAccuracyMeters)} · Senal: ${trackerState.gpsSignalLabel}",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (trackerState.isGpsUnstable || trackerState.discardedGpsSegments > 0 || trackerState.discardedGpsPoints > 0) {
                        Text(
                            "GPS inestable: ${trackerState.discardedGpsPoints + trackerState.discardedGpsSegments} lecturas descartadas.",
                            color = Color(0xFFFFD166),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it.filter(Char::isDigit) },
                        label = { Text("Calorías estimadas") },
                        supportingText = { Text("Opcional: ingresa si quieres ajustar", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hasLocationPermission && !trackerState.hasPreciseLocation) {
                        Text(
                            "Activa ubicación precisa para medir mejor tu recorrido.",
                            color = Color(0xFFFFD166),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (trackerState.status == TrackerStatus.FINISHED) {
                        FinishedActivitySummary(
                            discipline = discipline,
                            distanceKm = trackerState.distanceKm,
                            elapsedSeconds = trackerState.elapsedSeconds,
                            averagePaceMinPerKm = trackerState.averagePaceMinPerKm,
                            averageSpeedKmh = trackerState.averageSpeedKmh,
                            calories = caloriesText.toIntOrNull() ?: 0,
                            points = trackerState.points
                        )
                    }

                    TrackerControls(
                        status = trackerState.status,
                        startLabel = startLabel,
                        canSave = canSave,
                        hasPermission = hasLocationPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onStart = { trackerViewModel.start(discipline) },
                        onPause = trackerViewModel::pause,
                        onResume = trackerViewModel::resume,
                        onFinish = trackerViewModel::finish,
                        onReset = { trackerViewModel.reset(discipline) },
                        onSave = {
                            viewModel.addGpsActivitySession(
                                discipline = discipline,
                                title = if (isRun) "Carrera GPS" else "Rodada GPS",
                                routePoints = trackerState.points,
                                startedAt = trackerState.startedAt,
                                endedAt = trackerState.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                durationSeconds = trackerState.elapsedSeconds,
                                caloriesBurned = caloriesText.toIntOrNull() ?: 0
                            )
                            trackerViewModel.reset(discipline)
                            onNavigate(historyRoute)
                        }
                    )

                    if (disciplineSessions.isEmpty()) {
                        Text(
                            if (isRun) "Sin carreras todavía. Inicia tu primera carrera." else "Sin rodadas todavía. Inicia tu primera rodada.",
                            color = Color.White.copy(alpha = 0.64f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureBlockedScreen(
    title: String,
    description: String,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(LocalThemeIsDark.current),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir ajustes")
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishedActivitySummary(
    discipline: UserDiscipline,
    distanceKm: Double,
    elapsedSeconds: Long,
    averagePaceMinPerKm: Double,
    averageSpeedKmh: Double,
    calories: Int,
    points: List<GpsRoutePoint>
) {
    val hasRoute = ActivityTrackingRules.filterUsableRoutePoints(points).size >= 2
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resumen final", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkMetric("Distancia", String.format(Locale.US, "%.2f km", distanceKm), Modifier.weight(1f))
                DarkMetric("Duración", formatDuration(elapsedSeconds), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkMetric(
                    if (discipline == UserDiscipline.RUNNING) "Ritmo prom." else "Vel. prom.",
                    if (discipline == UserDiscipline.RUNNING) formatPace(averagePaceMinPerKm, "Calculando...") else String.format(Locale.US, "%.1f km/h", averageSpeedKmh),
                    Modifier.weight(1f)
                )
                DarkMetric("Calorías", if (calories > 0) "$calories kcal" else "Opcional", Modifier.weight(1f))
            }
            Text(
                if (hasRoute) "La ruta queda visible en el mapa antes de guardar." else "Aún no hay suficientes puntos GPS para dibujar una ruta confiable.",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Presiona 'Guardar sesión' para registrar y completar tu sesión.",
                color = Color(0xFF7CFF4F),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val sessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val workouts by viewModel.allWorkoutLogs.collectAsStateWithLifecycle()
    val healthLogs by viewModel.allHealthLogs.collectAsStateWithLifecycle()
    val hybridSettings by viewModel.hybridPlanSettings.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val todayStart = remember { disciplineStartOfTodayMillis() }
    val hasStrengthToday = workouts.any { it.timestamp >= todayStart }
    val cardioToday = sessions.filter {
        it.timestamp >= todayStart &&
            (it.discipline == UserDiscipline.RUNNING.name || it.discipline == UserDiscipline.CYCLING.name)
    }
    val hasWellnessToday = healthLogs.any { it.timestamp >= todayStart }
    val cardioSummary = remember(cardioToday) { ActivitySessionSummaryRules.summarize(cardioToday) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.testTag("daily_plan_screen").frostedMeshBackground(isDark),
        topBar = {
            TopAppBar(
                title = { Text("Plan del día", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "Combina fuerza, cardio y bienestar según tus ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item {
                Text(
                    "Objetivo ${hybridSettings.objective.lowercase(Locale.getDefault())}: ${hybridSettings.strengthDaysPerWeek} días de fuerza, ${hybridSettings.cardioDaysPerWeek} de cardio, ${hybridSettings.sessionMinutes} min por sesión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = { onNavigate(Destinations.SETTINGS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ajustar plan")
                }
            }
            item {
                DailyPlanActionCard(
                    title = if (hasStrengthToday) "Fuerza registrada" else "Fuerza",
                    description = if (hasStrengthToday) {
                        "Ya completaste una sesión de fuerza hoy."
                    } else {
                        "Haz una rutina breve y registra series, reps y carga."
                    },
                    icon = Icons.Default.FitnessCenter,
                    actionText = if (hasStrengthToday) "Ver rutinas" else "Entrenar",
                    onClick = { onNavigate(Destinations.WORKOUT) }
                )
            }
            item {
                DailyPlanActionCard(
                    title = if (cardioToday.isEmpty()) "Cardio" else "Cardio registrado",
                    description = cardioSummary.distanceText?.let {
                        "$it hoy. ${cardioSummary.sessionCountText} guardadas."
                    } ?: "Registra cardio según tus ajustes: ${enabledHybridCardioText(hybridSettings.runningEnabled, hybridSettings.walkingEnabled, hybridSettings.cyclingEnabled)}.",
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    actionText = "Registrar carrera",
                    onClick = { onNavigate(if (hybridSettings.runningEnabled || hybridSettings.walkingEnabled) Destinations.ACTIVE_RUN else Destinations.ACTIVE_RIDE) },
                    secondaryActionText = if (hybridSettings.cyclingEnabled) "Registrar rodada" else null,
                    onSecondaryClick = if (hybridSettings.cyclingEnabled) ({ onNavigate(Destinations.ACTIVE_RIDE) }) else null
                )
            }
            item {
                DailyPlanActionCard(
                    title = if (hasWellnessToday) "Bienestar registrado" else "Bienestar",
                    description = if (hasWellnessToday) {
                        "Ya hay entradas de bienestar hoy."
                    } else {
                        "Agrega agua, sueno, energia, peso o check-in."
                    },
                    icon = Icons.Default.MonitorHeart,
                    actionText = "Abrir diario",
                    onClick = { onNavigate(Destinations.WELLNESS_DIARY) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningHistoryScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActivityHistoryScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.RUNNING,
        title = "Historial de carreras",
        emptyText = "Sin carreras todavía",
        onNavigate = onNavigate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyclingHistoryScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActivityHistoryScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.CYCLING,
        title = "Historial de rodadas",
        emptyText = "Sin rodadas todavía",
        onNavigate = onNavigate
    )
}

@Composable
fun WalkHistoryScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    ActivityHistoryScreen(
        viewModel = viewModel,
        discipline = UserDiscipline.WALKING,
        title = "Historial de caminatas",
        emptyText = "Sin caminatas todavía",
        onNavigate = onNavigate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityHistoryScreen(
    viewModel: FitPlanViewModel,
    discipline: UserDiscipline,
    title: String,
    emptyText: String,
    onNavigate: (String) -> Unit
) {
    val sessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val filtered = remember(sessions, discipline) {
        sessions.filter { it.discipline == discipline.name }
    }
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .testTag("${discipline.name.lowercase()}_history_screen")
            .frostedMeshBackground(LocalThemeIsDark.current),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filtered.isEmpty()) {
                item { EmptyDisciplineState(emptyText) }
            } else {
                items(filtered, key = { it.id }) { session ->
                    ActivitySessionRow(
                        session = session,
                        discipline = discipline,
                        onClick = { onNavigate("${Destinations.ACTIVITY_SESSION_DETAIL}/${session.id}") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySessionDetailScreen(
    viewModel: FitPlanViewModel,
    sessionId: Long,
    onNavigate: (String) -> Unit
) {
    val sessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val session = remember(sessions, sessionId) { sessions.firstOrNull { it.id == sessionId } }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.testTag("activity_session_detail_screen").frostedMeshBackground(LocalThemeIsDark.current),
        topBar = {
            TopAppBar(
                title = { Text("Detalle de sesión", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Sesión no encontrada")
            }
        } else {
            val discipline = UserDiscipline.fromStoredName(session.discipline)
            val points = remember(session.routePointsJson) {
                ActivityTrackingRules.fromRouteJson(session.routePointsJson)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    GpsMapPanel(
                        points = points,
                        currentLocation = points.lastOrNull(),
                        discipline = discipline,
                        message = if (points.isEmpty()) "Esta sesión no tiene ruta GPS guardada" else "Ruta guardada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        isHistoryMode = true
                    )
                }
                item {
                    ActivitySummaryCard(
                        discipline = discipline,
                        distance = String.format(Locale.US, "%.2f km", session.distanceKm),
                        metric = if (discipline == UserDiscipline.RUNNING) {
                            formatPace(session.averagePaceMinPerKm.takeIf { it > 0.0 } ?: ActivityTrackingRules.summarize(points, session.durationSeconds, discipline).averagePaceMinPerKm)
                        } else {
                            String.format(Locale.US, "%.1f km/h", session.averageSpeedKmh.takeIf { it > 0.0 } ?: ActivityTrackingRules.summarize(points, session.durationSeconds, discipline).averageSpeedKmh)
                        },
                        sessions = formatDuration(session.durationSeconds.takeIf { it > 0L } ?: session.durationMinutes * 60L)
                    )
                }
                item {
                    OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Eliminar sesión")
                    }
                }
            }
        }
    }

    val deleteSession = session
    if (showDeleteDialog && deleteSession != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar sesión") },
            text = { Text("Se eliminara esta actividad local y su ruta guardada.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeActivitySession(deleteSession)
                        showDeleteDialog = false
                        onNavigate(Destinations.DASHBOARD)
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySessionLogScreen(
    viewModel: FitPlanViewModel,
    discipline: UserDiscipline,
    title: String,
    distanceLabel: String,
    durationLabel: String,
    estimatedMetricLabel: String,
    emptyText: String,
    icon: ImageVector,
    onNavigate: (String) -> Unit
) {
    val allSessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val sessions = remember(allSessions, discipline) {
        allSessions.filter { it.discipline == discipline.name }
    }
    val summary = remember(sessions) { ActivitySessionSummaryRules.summarize(sessions) }
    val isDark = LocalThemeIsDark.current
    var distanceText by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val distanceKm by remember(distanceText) {
        derivedStateOf { distanceText.normalizedFloatOrNull() }
    }
    val durationMinutes by remember(durationText) {
        derivedStateOf { durationText.toIntOrNull() }
    }
    val estimatedMetric by remember(distanceKm, durationMinutes, discipline) {
        derivedStateOf {
            val distance = distanceKm
            val duration = durationMinutes
            if (distance == null || duration == null || distance <= 0f || duration <= 0) {
                "Ingresa distancia y duración"
            } else if (discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) {
                String.format(Locale.US, "%.1f min/km", duration / distance)
            } else {
                String.format(Locale.US, "%.1f km/h", distance / (duration / 60f))
            }
        }
    }
    val canSave = distanceKm != null && distanceKm!! > 0f &&
        durationMinutes != null && durationMinutes!! > 0

    fun saveSession() {
        val distance = distanceKm
        val duration = durationMinutes
        if (distance == null || duration == null || distance <= 0f || duration <= 0) {
            errorText = "Agrega distancia y duración mayores a cero."
            return
        }
        viewModel.addActivitySession(
            discipline = discipline,
            title = title,
            distanceKm = distance,
            durationMinutes = duration,
            caloriesBurned = caloriesText.toIntOrNull() ?: 0,
            notes = notesText
        )
        onNavigate(Destinations.DASHBOARD)
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Destinations.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Nueva sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = distanceText,
                                onValueChange = {
                                    distanceText = it
                                    errorText = null
                                },
                                label = { Text(distanceLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = durationText,
                                onValueChange = {
                                    durationText = it.filter(Char::isDigit)
                                    errorText = null
                                },
                                label = { Text(durationLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = caloriesText,
                            onValueChange = { caloriesText = it.filter(Char::isDigit) },
                            label = { Text("Calorías opcionales") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Notas opcionales") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$estimatedMetricLabel: $estimatedMetric",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        errorText?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = ::saveSession, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                            Text("Guardar sesión")
                        }
                    }
                }
            }

            item {
                ActivitySummaryCard(
                    discipline = discipline,
                    distance = summary.distanceText ?: "Sin datos",
                    metric = if (discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) {
                        summary.paceText ?: "Sin datos"
                    } else {
                        summary.speedText ?: "Sin datos"
                    },
                    sessions = summary.sessionCountText
                )
            }

            item {
                Text("Ver historial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (sessions.isEmpty()) {
                item {
                    EmptyDisciplineState(text = emptyText)
                }
            } else {
                items(sessions.take(10), key = { it.id }) { session ->
                    ActivitySessionRow(session = session, discipline = discipline)
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    discipline: UserDiscipline,
    distance: String,
    metric: String,
    sessions: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallStat("Distancia", distance, Modifier.weight(1f))
            SmallStat(if (discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) "Ritmo" else "Velocidad", metric, Modifier.weight(1f))
            SmallStat("Sesiones", sessions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivitySessionRow(
    session: ActivitySessionEntity,
    discipline: UserDiscipline,
    onClick: (() -> Unit)? = null
) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val metric = if (session.distanceKm > 0f && session.durationMinutes > 0) {
        if ((discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) && session.distanceKm >= 0.05f) {
            formatPace(session.averagePaceMinPerKm.takeIf { it > 0.0 } ?: (session.durationMinutes / session.distanceKm.toDouble()))
        } else if (discipline == UserDiscipline.CYCLING) {
            String.format(Locale.US, "%.1f km/h", session.distanceKm / (session.durationMinutes / 60f))
        } else {
            "Sin datos confiables"
        }
    } else {
        "Sin datos"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatter.format(Date(session.timestamp)), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                String.format(Locale.US, "%.1f km / %d min / %s", session.distanceKm, session.durationMinutes, metric),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.caloriesBurned > 0) {
                Text("${session.caloriesBurned} kcal", style = MaterialTheme.typography.bodySmall)
            }
            if (session.notes.isNotBlank()) {
                Text(session.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GpsMapPanel(
    points: List<GpsRoutePoint>,
    currentLocation: GpsRoutePoint?,
    discipline: UserDiscipline,
    message: String,
    modifier: Modifier = Modifier,
    isHistoryMode: Boolean = false
) {
    val context = LocalContext.current
    val usablePoints = remember(points, discipline) { ActivityTrackingRules.filterUsableRoutePoints(points, discipline) }
    val cameraTarget = currentLocation ?: usablePoints.lastOrNull()
    var followUser by remember { mutableStateOf(true) }
    val routeGeoJson = remember(usablePoints, discipline) {
        if (usablePoints.size >= 2) {
            ActivityTrackingRules.toRouteGeoJson(usablePoints, discipline)
        } else {
            EmptyRouteGeoJson
        }
    }
    val userLocationGeoJson = remember(currentLocation) {
        currentLocation?.let(::toUserLocationGeoJson) ?: EmptyRouteGeoJson
    }
    val cameraMoveStartedListener = remember {
        MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                followUser = false
            }
        }
    }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }
    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    DisposableEffect(mapView, cameraMoveStartedListener) {
        mapView.getMapAsync { map ->
            map.addOnCameraMoveStartedListener(cameraMoveStartedListener)
        }
        onDispose {
            mapView.getMapAsync { map ->
                map.removeOnCameraMoveStartedListener(cameraMoveStartedListener)
            }
        }
    }

    Box(modifier = modifier.heightIn(min = 260.dp)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF0F172A), RoundedCornerShape(22.dp)),
            update = { view ->
                view.getMapAsync { map ->
                    map.uiSettings.isCompassEnabled = false
                    val updateStyle: (Style) -> Unit = { style ->
                        updateMapLibreRoute(
                            map = map,
                            style = style,
                            points = usablePoints,
                            currentLocation = currentLocation,
                            routeGeoJson = routeGeoJson,
                            userLocationGeoJson = userLocationGeoJson,
                            followUser = followUser,
                            isHistoryMode = isHistoryMode
                        )
                    }
                    val currentStyle = map.style
                    if (currentStyle == null || currentStyle.uri != OpenFreeMapStyleUrl) {
                        map.setStyle(OpenFreeMapStyleUrl, updateStyle)
                    } else {
                        updateStyle(currentStyle)
                    }
                }
            }
        )
        if (cameraTarget == null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x9907111F)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF7CFF4F), modifier = Modifier.size(36.dp))
                    Text(message, color = Color(0xFF7CFF4F), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (cameraTarget != null) {
            IconButton(
                onClick = { followUser = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color(0xCC111827), RoundedCornerShape(14.dp))
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mi", tint = Color(0xFF7CFF4F))
            }
        }
    }
}

private const val OpenFreeMapStyleUrl = "https://tiles.openfreemap.org/styles/liberty"
private const val RouteSourceId = "fitplan-route-source"
private const val RouteLayerId = "fitplan-route-layer"
private const val UserLocationSourceId = "fitplan-user-location-source"
private const val UserLocationLayerId = "fitplan-user-location-layer"
private const val EmptyRouteGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}"

private fun updateMapLibreRoute(
    map: MapLibreMap,
    style: Style,
    points: List<GpsRoutePoint>,
    currentLocation: GpsRoutePoint?,
    routeGeoJson: String,
    userLocationGeoJson: String,
    followUser: Boolean,
    isHistoryMode: Boolean
) {
    val routeSource = style.getSourceAs<GeoJsonSource>(RouteSourceId)
    if (routeSource == null) {
        style.addSource(GeoJsonSource(RouteSourceId, routeGeoJson))
    } else {
        routeSource.setGeoJson(routeGeoJson)
    }
    if (style.getLayer(RouteLayerId) == null) {
        style.addLayer(
            LineLayer(RouteLayerId, RouteSourceId).withProperties(
                lineColor(AndroidColor.parseColor("#7CFF4F")),
                lineWidth(9f)
            )
        )
    }
    val userSource = style.getSourceAs<GeoJsonSource>(UserLocationSourceId)
    if (userSource == null) {
        style.addSource(GeoJsonSource(UserLocationSourceId, userLocationGeoJson))
    } else {
        userSource.setGeoJson(userLocationGeoJson)
    }
    if (style.getLayer(UserLocationLayerId) == null) {
        style.addLayer(
            CircleLayer(UserLocationLayerId, UserLocationSourceId).withProperties(
                circleColor(AndroidColor.parseColor("#2F80ED")),
                circleRadius(8f),
                circleStrokeColor(AndroidColor.parseColor("#FFFFFF")),
                circleStrokeWidth(3f)
            )
        )
    }
    
    if (isHistoryMode && points.isNotEmpty()) {
        val bounds = LatLngBounds.Builder()
        points.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
    } else if (followUser) {
        val cameraPoint = currentLocation ?: points.lastOrNull()
        cameraPoint?.let { point ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
        }
    }
}

private fun toUserLocationGeoJson(point: GpsRoutePoint): String {
    return String.format(
        Locale.US,
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f,%.7f]},\"properties\":{}}",
        point.longitude,
        point.latitude
    )
}

@Composable
private fun DarkMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrackerControls(
    status: TrackerStatus,
    startLabel: String,
    canSave: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    when {
        !hasPermission || status == TrackerStatus.NEEDS_PERMISSION -> {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().testTag("tracker_permission_button")) {
                Text("Permitir ubicación")
            }
        }
        status == TrackerStatus.READY -> {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("tracker_start_button")) {
                Text(startLabel, fontWeight = FontWeight.Bold)
            }
        }
        status == TrackerStatus.TRACKING || status == TrackerStatus.SEARCHING_GPS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).testTag("tracker_pause_button")) { Text("Pausar") }
                Button(onClick = onFinish, modifier = Modifier.weight(1f).testTag("tracker_finish_button")) { Text("Finalizar") }
            }
        }
        status == TrackerStatus.PAUSED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f).testTag("tracker_resume_button")) { Text("Reanudar") }
                Button(onClick = onFinish, modifier = Modifier.weight(1f).testTag("tracker_finish_button")) { Text("Finalizar") }
            }
        }
        status == TrackerStatus.FINISHED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).testTag("tracker_discard_button")) { Text("Descartar") }
                Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f).testTag("tracker_save_button")) { Text("Guardar sesión") }
            }
            if (!canSave) {
                Text(
                    "No se guarda si la distancia es 0 o la duración es demasiado baja.",
                    color = Color.White.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        status == TrackerStatus.ERROR -> {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("tracker_retry_button")) {
                Text("Reintentar GPS")
            }
        }
        else -> {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(startLabel)
            }
        }
    }
}

@Composable
private fun DailyPlanActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    actionText: String,
    onClick: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                    Text(actionText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (secondaryActionText != null && onSecondaryClick != null) {
                    OutlinedButton(onClick = onSecondaryClick, modifier = Modifier.weight(1f)) {
                        Text(secondaryActionText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDisciplineState(text: String) {
    val isCycling = text.contains("rodadas", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Sin sesiones todavía", fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (isCycling) {
                    "Toca 'Iniciar rodada' o registra una actividad manual para comenzar."
                } else {
                    "Toca 'Iniciar carrera' o registra una actividad manual para comenzar."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

private fun String.normalizedFloatOrNull(): Float? {
    return replace(",", ".").toFloatOrNull()
}

private fun formatDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val secs = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

private fun formatPace(value: Double, pendingLabel: String = "Sin datos"): String {
    return if (value > 0.0) String.format(Locale.US, "%.1f min/km", value) else pendingLabel
}

private fun formatGpsAccuracy(value: Float): String {
    return if (value > 0f) {
        String.format(Locale.US, "±%.0f m", value)
    } else {
        "Sin datos"
    }
}

private fun trackerStatusLabel(status: TrackerStatus): String {
    return when (status) {
        TrackerStatus.READY -> "Listo"
        TrackerStatus.NEEDS_PERMISSION -> "Permiso requerido"
        TrackerStatus.SEARCHING_GPS -> "Buscando GPS"
        TrackerStatus.TRACKING -> "Grabando"
        TrackerStatus.PAUSED -> "Pausado"
        TrackerStatus.FINISHED -> "Finalizado"
        TrackerStatus.ERROR -> "Error"
    }
}

private fun enabledHybridCardioText(run: Boolean, walk: Boolean, bike: Boolean): String {
    val enabled = buildList {
        if (run) add("carrera")
        if (walk) add("caminata")
        if (bike) add("bici")
    }
    return enabled.ifEmpty { listOf("descanso activo") }.joinToString()
}

private fun disciplineStartOfTodayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
