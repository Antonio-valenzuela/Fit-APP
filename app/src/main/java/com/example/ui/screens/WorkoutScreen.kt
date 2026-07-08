package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserDiscipline
import com.example.data.model.Muscle
import com.example.ui.muscles.MuscleMapViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.frostedMeshBackground
import com.example.features.gym.ui.GymViewModel
import com.example.ui.workout.ExerciseCatalogItem
import com.example.ui.workout.SuggestedRoutine
import com.example.ui.workout.TrainingCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WorkoutScreen(viewModel: GymViewModel, onNavigate: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workouts = state.workouts
    val isPremium = state.isPremiumActive
    val user = state.user
    val discipline = remember(user?.trainingDiscipline) { UserDiscipline.fromStoredName(user?.trainingDiscipline) }
    val muscleViewModel: MuscleMapViewModel = hiltViewModel()
    val muscleState by muscleViewModel.state.collectAsStateWithLifecycle()

    var selectedMuscle by remember { mutableStateOf<Muscle?>(null) }
    var selectedRoutine by remember { mutableStateOf<SuggestedRoutine?>(null) }
    var activeRoutine by remember { mutableStateOf<SuggestedRoutine?>(null) }
    var activeExerciseIndex by remember { mutableIntStateOf(0) }
    var completedSets by remember { mutableIntStateOf(0) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var repsStr by remember { mutableStateOf("10") }
    var loadStr by remember { mutableStateOf("") }
    var rpeStr by remember { mutableStateOf("7") }
    var selectedTime by remember { mutableIntStateOf(30) }
    var selectedEquipment by remember { mutableStateOf("Sin equipo") }
    var selectedObjective by remember { mutableStateOf("Fuerza") }

    LaunchedEffect(muscleState.recommendation?.muscle) {
        muscleState.recommendation?.muscle?.let { selectedMuscle = it }
    }

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning && isActive) {
            delay(1000)
            timerSeconds++
        }
    }

    fun startRoutine(routine: SuggestedRoutine) {
        selectedRoutine = routine
        activeRoutine = routine
        activeExerciseIndex = 0
        completedSets = 0
        timerSeconds = 0
        isTimerRunning = true
        TrainingCatalog.exerciseById(routine.exerciseIds.firstOrNull().orEmpty())?.let { exercise ->
            repsStr = exercise.suggestedRepsNumber()
            loadStr = ""
            rpeStr = "7"
        }
    }

    fun finishWorkout() {
        val routine = activeRoutine ?: return
        isTimerRunning = false
        val durationMins = (timerSeconds / 60).coerceAtLeast(1)
        val exercises = routine.exerciseIds.mapNotNull(TrainingCatalog::exerciseById)
        val summary = exercises.joinToString(separator = "; ") { exercise ->
            "${exercise.name}: ${exercise.suggestedSets} series x ${exercise.suggestedReps}, Peso: ${loadStr.ifBlank { "sin peso" }}, RPE: ${rpeStr.ifBlank { "7" }}"
        }
        viewModel.registerWorkout(
            name = routine.name,
            duration = durationMins,
            calories = (durationMins * 7).coerceAtLeast(10),
            repsExercises = summary
        )
        activeRoutine = null
        activeExerciseIndex = 0
        completedSets = 0
        timerSeconds = 0
        repsStr = "10"
        loadStr = ""
        rpeStr = "7"
    }

    val isDark = LocalThemeIsDark.current
    val recommendedMuscle = muscleState.recommendation?.muscle ?: selectedMuscle ?: Muscle.CHEST
    val recommendedRoutine = remember(recommendedMuscle) {
        TrainingCatalog.routineForMuscle(recommendedMuscle.legacyName)
    }
    val filteredRoutines = remember(selectedTime, selectedEquipment, selectedObjective) {
        TrainingCatalog.filteredRoutines(selectedTime, selectedEquipment, selectedObjective)
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        bottomBar = { FitPlanBottomBar(Destinations.WORKOUT, onNavigate, isPremium, discipline) }
    ) { innerPadding ->
        if (activeRoutine != null) {
            ActiveWorkoutContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                routine = activeRoutine!!,
                activeExerciseIndex = activeExerciseIndex,
                completedSets = completedSets,
                timerSeconds = timerSeconds,
                isTimerRunning = isTimerRunning,
                repsStr = repsStr,
                loadStr = loadStr,
                rpeStr = rpeStr,
                onToggleTimer = { isTimerRunning = !isTimerRunning },
                onResetTimer = { timerSeconds = 0 },
                onRepsChange = { repsStr = it },
                onLoadChange = { loadStr = it },
                onRpeChange = { rpeStr = it },
                onCompleteSet = {
                    val exercise = TrainingCatalog.exerciseById(activeRoutine!!.exerciseIds[activeExerciseIndex])
                    val targetSets = exercise?.suggestedSets ?: 3
                    if (completedSets + 1 >= targetSets && activeExerciseIndex < activeRoutine!!.exerciseIds.lastIndex) {
                        activeExerciseIndex++
                        completedSets = 0
                    } else {
                        completedSets++
                    }
                },
                onNextExercise = {
                    if (activeExerciseIndex < activeRoutine!!.exerciseIds.lastIndex) {
                        activeExerciseIndex++
                        completedSets = 0
                    }
                },
                onFinish = ::finishWorkout
            )
        } else {
            val screenInfo = LocalScreenInfo.current
            val spacing = screenInfo.standardSpacing
            val columns = if (screenInfo.screenType == ScreenType.Expanded) 3 else 2
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Que vas a entrenar hoy?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Elige una rutina sugerida, explora por musculo o crea una sesion libre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    TrainingProgressShortcutCard(
                        onProgress = { onNavigate(Destinations.PROGRESS) }
                    )
                }

                item {
                    RecommendedTrainingCard(
                        muscle = recommendedMuscle,
                        routine = recommendedRoutine,
                        reason = muscleState.recommendation?.reason,
                        onStart = { startRoutine(recommendedRoutine) },
                        onBalance = { onNavigate(Destinations.MUSCLE_MAP) }
                    )
                }

                if (workouts.isNotEmpty()) {
                    item {
                        ContinueLastRoutineCard(
                            title = workouts.first().routineName,
                            onStart = {
                                val match = TrainingCatalog.routines.firstOrNull { it.name == workouts.first().routineName }
                                    ?: TrainingCatalog.routines.first()
                                startRoutine(match)
                            }
                        )
                    }
                }

                item {
                    val expressRoutine = TrainingCatalog.routines.firstOrNull { it.id == "express_15" }
                    expressRoutine?.let {
                        ExpressHighlightCard(
                            routine = it,
                            onPreview = { selectedRoutine = it },
                            onStart = { startRoutine(it) }
                        )
                    }
                }

                item {
                    SectionTitle("Rutinas por tiempo y equipo")
                    RoutineFilters(
                        selectedTime = selectedTime,
                        onTime = { selectedTime = it },
                        selectedEquipment = selectedEquipment,
                        onEquipment = { selectedEquipment = it },
                        selectedObjective = selectedObjective,
                        onObjective = { selectedObjective = it }
                    )
                }

                if (screenInfo.isTabletOrWider) {
                    val chunks = filteredRoutines.chunked(columns)
                    items(chunks) { rowRoutines ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            rowRoutines.forEach { routine ->
                                Box(modifier = Modifier.weight(1f)) {
                                    RoutineCard(
                                        routine = routine,
                                        onPreview = { selectedRoutine = routine },
                                        onStart = { startRoutine(routine) }
                                    )
                                }
                            }
                            repeat(columns - rowRoutines.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredRoutines) { routine ->
                        RoutineCard(
                            routine = routine,
                            onPreview = { selectedRoutine = routine },
                            onStart = { startRoutine(routine) }
                        )
                    }
                }

                item {
                    SectionTitle("Explorar por musculo")
                    MuscleSelector(
                        selectedMuscle = selectedMuscle ?: Muscle.CHEST,
                        onSelect = { selectedMuscle = it }
                    )
                }

                val muscleExercises = TrainingCatalog.exercisesForMuscle((selectedMuscle ?: Muscle.CHEST).legacyName)
                if (muscleExercises.isEmpty()) {
                    item {
                        EmptyTrainingCard("No hay ejercicios en esta categoria todavia.")
                    }
                } else {
                    if (screenInfo.isTabletOrWider) {
                        val chunks = muscleExercises.chunked(columns)
                        items(chunks) { rowExercises ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                rowExercises.forEach { exercise ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ExerciseCard(exercise)
                                    }
                                }
                                repeat(columns - rowExercises.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(muscleExercises) { exercise ->
                            ExerciseCard(exercise)
                        }
                    }
                }

                item {
                    FreeWorkoutCard(onStart = { startRoutine(TrainingCatalog.routines.first { it.id == "beginner_full_body" }) })
                }

                item {
                    SectionTitle("Historial")
                }
                if (workouts.isEmpty()) {
                    item {
                        EmptyTrainingCard("Todavia no tienes rutinas guardadas. Puedes iniciar una sugerida o crear una libre.")
                    }
                } else {
                    val historyItems = workouts.take(6)
                    if (screenInfo.isTabletOrWider) {
                        val chunks = historyItems.chunked(columns)
                        items(chunks) { rowHistory ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                                rowHistory.forEach { log ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        WorkoutHistoryCard(log.routineName, log.durationMinutes, log.caloriesBurned, log.exercisesText) {
                                            viewModel.removeWorkoutLog(log)
                                        }
                                    }
                                }
                                repeat(columns - rowHistory.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(historyItems) { log ->
                            WorkoutHistoryCard(log.routineName, log.durationMinutes, log.caloriesBurned, log.exercisesText) {
                                viewModel.removeWorkoutLog(log)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    selectedRoutine?.let { routine ->
        Dialog(onDismissRequest = { selectedRoutine = null }) {
            RoutinePreviewCard(
                routine = routine,
                onStart = {
                    selectedRoutine = null
                    startRoutine(routine)
                },
                onClose = { selectedRoutine = null }
            )
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    modifier: Modifier,
    routine: SuggestedRoutine,
    activeExerciseIndex: Int,
    completedSets: Int,
    timerSeconds: Int,
    isTimerRunning: Boolean,
    repsStr: String,
    loadStr: String,
    rpeStr: String,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onRepsChange: (String) -> Unit,
    onLoadChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onNextExercise: () -> Unit,
    onFinish: () -> Unit
) {
    val exercises = routine.exerciseIds.mapNotNull(TrainingCatalog::exerciseById)
    val current = exercises.getOrNull(activeExerciseIndex)
    val totalExercises = exercises.size.coerceAtLeast(1)
    val routineProgress = (activeExerciseIndex + 1) / totalExercises.toFloat()
    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Entrenamiento activo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(routine.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format("%02d:%02d", minutes, seconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onToggleTimer, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                                Icon(if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Pausar o continuar", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            IconButton(onClick = onResetTimer, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reiniciar tiempo")
                            }
                        }
                    }
                    LinearProgressIndicator(progress = { routineProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)))
                    Text("Ejercicio ${activeExerciseIndex + 1} de $totalExercises", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current?.name ?: "Ejercicio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(current?.instructions ?: "Sigue el plan con control y buena tecnica.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Serie ${completedSets + 1} de ${current?.suggestedSets ?: 3}", fontWeight = FontWeight.SemiBold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = loadStr,
                            onValueChange = onLoadChange,
                            label = { Text("Peso") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repsStr,
                            onValueChange = onRepsChange,
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = rpeStr,
                        onValueChange = onRpeChange,
                        label = { Text("Esfuerzo percibido (RPE)") },
                        supportingText = { Text("1 facil - 10 maximo esfuerzo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = onCompleteSet, modifier = Modifier.fillMaxWidth().testTag("complete_set_btn")) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Completar serie", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onNextExercise, modifier = Modifier.fillMaxWidth(), enabled = activeExerciseIndex < routine.exerciseIds.lastIndex) {
                        Text("Siguiente ejercicio")
                    }
                    OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().testTag("save_workout_btn")) {
                        Text("Finalizar y guardar entrenamiento", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Musculos de esta sesion", fontWeight = FontWeight.Bold)
                    Text(routine.primaryMuscles.joinToString { Muscle.fromLegacyName(it)?.displayName ?: it }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrainingProgressShortcutCard(onProgress: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Atajo de progreso", fontWeight = FontWeight.Bold)
                Text(
                    "Revisa tendencias, salud y balance cuando termines de entrenar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onProgress) {
                Text("Ver")
            }
        }
    }
}

@Composable
private fun RecommendedTrainingCard(
    muscle: Muscle,
    routine: SuggestedRoutine,
    reason: String?,
    onStart: () -> Unit,
    onBalance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recomendado por balance muscular", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${muscle.displayName}: ${routine.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(reason ?: "Tu balance se ve estable. Puedes elegir cualquier rutina de hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text("Iniciar rutina", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onBalance, modifier = Modifier.weight(1f)) {
                    Text("Ver balance")
                }
            }
        }
    }
}

@Composable
private fun ContinueLastRoutineCard(title: String, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onStart() }.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Duplicar rutina anterior", fontWeight = FontWeight.Bold)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun RoutineFilters(
    selectedTime: Int,
    onTime: (Int) -> Unit,
    selectedEquipment: String,
    onEquipment: (String) -> Unit,
    selectedObjective: String,
    onObjective: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                FilterChip(
                    selected = selectedTime == minutes,
                    onClick = { onTime(minutes) },
                    label = { Text("${minutes} min") }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sin equipo", "Casa", "Gym").forEach { equipment ->
                FilterChip(
                    selected = selectedEquipment == equipment,
                    onClick = { onEquipment(equipment) },
                    label = { Text(equipment) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fuerza", "Hipertrofia", "Bajar grasa", "Resistencia", "Movilidad").forEach { objective ->
                FilterChip(
                    selected = selectedObjective == objective,
                    onClick = { onObjective(objective) },
                    label = { Text(objective) }
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: SuggestedRoutine, onPreview: () -> Unit, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(routine.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${routine.estimatedMinutes} min - ${routine.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(routine.objective, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${routine.equipment} - ${routine.focus}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(routine.primaryMuscles.joinToString { Muscle.fromLegacyName(it)?.displayName ?: it }, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Ver detalles") }
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Iniciar entrenamiento") }
            }
        }
    }
}

@Composable
private fun RoutinePreviewCard(routine: SuggestedRoutine, onStart: () -> Unit, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detalle de rutina", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(routine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(routine.focus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${routine.estimatedMinutes} min - ${routine.level} - ${routine.objective}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Equipo: ${routine.equipment}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Musculos: ${routine.primaryMuscles.joinToString { Muscle.fromLegacyName(it)?.displayName ?: it }}", style = MaterialTheme.typography.bodySmall)
            routine.exerciseIds.mapNotNull(TrainingCatalog::exerciseById).forEach { exercise ->
                Text("${exercise.name} - ${exercise.suggestedSets} series x ${exercise.suggestedReps}", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Iniciar entrenamiento") }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cerrar detalle") }
        }
    }
}

@Composable
private fun MuscleSelector(selectedMuscle: Muscle, onSelect: (Muscle) -> Unit) {
    val muscles = listOf(Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABS, Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CALVES)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        muscles.forEach { muscle ->
            FilterChip(
                selected = selectedMuscle == muscle,
                onClick = { onSelect(muscle) },
                label = { Text(muscle.displayName) }
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseCatalogItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, fontWeight = FontWeight.Bold)
            Text("${Muscle.fromLegacyName(exercise.primaryMuscle)?.displayName ?: exercise.primaryMuscle} - ${exercise.equipment} - ${exercise.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(exercise.instructions, style = MaterialTheme.typography.bodyMedium)
            Text("${exercise.suggestedSets} series x ${exercise.suggestedReps} - descanso ${exercise.restSeconds}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FreeWorkoutCard(onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onStart() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Iniciar base full body", fontWeight = FontWeight.Bold)
                Text("Empieza con una rutina base y ajusta el registro durante la sesion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WorkoutHistoryCard(title: String, duration: Int, calories: Int, summary: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text("$duration min - $calories kcal estimadas", style = MaterialTheme.typography.bodySmall)
                Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = AlertRed)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ExpressHighlightCard(
    routine: SuggestedRoutine,
    onPreview: () -> Unit,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sesión rápida", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${routine.estimatedMinutes} min",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Sesión rápida de 15 min. Perfecta si tienes poco tiempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "✓ Sin equipo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "✓ Cuerpo completo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("Ver detalles") }
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text("Iniciar ahora", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyTrainingCard(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ExerciseCatalogItem.suggestedRepsNumber(): String {
    return Regex("\\d+").find(suggestedReps)?.value ?: "10"
}
