package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.report.HealthReportContentBuilder
import com.example.data.report.HealthReportPdfGenerator
import com.example.data.model.AppPerformanceMode
import com.example.data.model.HybridPlanSettings
import com.example.data.model.UserDiscipline
import com.example.data.model.getTrainingPlace
import com.example.ui.theme.AlertRed
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FitPlanViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = { onNavigate(Destinations.PROFILE) }
) {
    val settings by viewModel.featureSettings.collectAsStateWithLifecycle()
    val performancePreferences by viewModel.performancePreferences.collectAsStateWithLifecycle()
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val defaultGlassMl by viewModel.defaultGlassMl.collectAsStateWithLifecycle()
    val hybridSettings by viewModel.hybridPlanSettings.collectAsStateWithLifecycle()
    val activeTheme by viewModel.themeSelector.collectAsStateWithLifecycle()
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val foodLogs by viewModel.allFoodLogs.collectAsStateWithLifecycle()
    val workoutLogs by viewModel.allWorkoutLogs.collectAsStateWithLifecycle()
    val healthLogs by viewModel.allHealthLogs.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val selectedDiscipline = remember(user?.trainingDiscipline) {
        UserDiscipline.fromStoredName(user?.trainingDiscipline)
    }
    val isDark = LocalThemeIsDark.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHealthNotice by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember(user) { mutableStateOf(user?.name.orEmpty()) }
    var editAge by remember(user) { mutableStateOf(user?.age?.toString().orEmpty()) }
    var editWeight by remember(user) { mutableStateOf(user?.weightKg?.toString().orEmpty()) }
    var editHeight by remember(user) { mutableStateOf(user?.heightCm?.toString().orEmpty()) }
    var editGender by remember(user) { mutableStateOf(user?.gender ?: "Masculino") }
    var editPlace by remember(user) { mutableStateOf(user?.getTrainingPlace() ?: "Ambos") }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message = message, withDismissAction = true) }
    }

    fun canUseNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun enableReminders() {
        viewModel.setRemindersEnabled(true)
        com.example.reminder.ReminderScheduler.scheduleDailyReminder(context)
        showMessage("Recordatorios activados.")
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableReminders()
        } else {
            viewModel.setRemindersEnabled(false)
            showMessage("Permiso de notificaciones no concedido.")
        }
    }

    fun exportLocalSummary() {
        try {
            val content = HealthReportContentBuilder.build(
                user = user,
                foodLogs = foodLogs,
                workoutLogs = workoutLogs,
                healthLogs = healthLogs
            )
            val pdf = HealthReportPdfGenerator.createPdf(context, content)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdf
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FitPlan MX - Informe de seguimiento personal")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar informe PDF"))
        } catch (e: Exception) {
            showMessage("No se pudo generar el PDF: ${e.localizedMessage ?: "error desconocido"}")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Limpiar datos locales") },
            text = {
                Text("Esto elimina tu perfil, registros y progreso guardado en este celular. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.purgeAccountData()
                        Toast.makeText(context, "Datos locales eliminados", Toast.LENGTH_LONG).show()
                        onNavigate(Destinations.LOGIN)
                    }
                ) { Text("Eliminar datos") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showHealthNotice) {
        AlertDialog(
            onDismissRequest = { showHealthNotice = false },
            title = { Text("Aviso de salud") },
            text = {
                Text("FitPlan ayuda a registrar hábitos y bienestar, pero no sustituye una consulta médica. Si tienes dolor, síntomas o dudas importantes, habla con un profesional de salud.")
            },
            confirmButton = {
                TextButton(onClick = { showHealthNotice = false }) { Text("Entendido") }
            }
        )
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Editar perfil", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it },
                        label = { Text("Edad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editWeight,
                        onValueChange = { editWeight = it },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editHeight,
                        onValueChange = { editHeight = it },
                        label = { Text("Estatura (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Sexo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Masculino", "Femenino").forEach { g ->
                            FilterChip(
                                selected = editGender == g,
                                onClick = { editGender = g },
                                label = { Text(g) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text("Lugar de entrenamiento", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Casa", "Gimnasio", "Ambos").forEach { option ->
                            FilterChip(
                                selected = editPlace == option,
                                onClick = { editPlace = option },
                                label = { Text(option, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val aNum = editAge.toIntOrNull() ?: 25
                        val wNum = editWeight.toFloatOrNull() ?: 70.0f
                        val hNum = editHeight.toFloatOrNull() ?: 170.0f
                        if (editName.isBlank() || aNum !in 10..100 || wNum !in 30f..250f || hNum !in 100f..250f) {
                            Toast.makeText(context, "Por favor revisa que todos los campos sean correctos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showEditProfileDialog = false
                        val currentGoal = user?.goal.orEmpty()
                        val newGoalString = if (currentGoal.contains("|")) {
                            val parts = currentGoal.split("|").map { it.trim() }.toMutableList()
                            var replaced = false
                            for (i in parts.indices) {
                                if (parts[i].startsWith("Lugar:", ignoreCase = true)) {
                                    parts[i] = "Lugar: $editPlace"
                                    replaced = true
                                    break
                                }
                            }
                            if (!replaced) {
                                parts.add("Lugar: $editPlace")
                            }
                            parts.joinToString(" | ")
                        } else {
                            "$currentGoal | Lugar: $editPlace"
                        }
                        val parsedGoal = if (currentGoal.contains("|")) currentGoal.substringBefore("|").trim() else currentGoal
                        viewModel.completeOnboarding(
                            name = editName,
                            age = aNum,
                            weightKg = wNum,
                            heightCm = hNum,
                            gender = editGender,
                            activityLevel = user?.activityLevel ?: "Moderado",
                            goal = newGoalString,
                            disclaimerAccepted = true,
                            trainingDiscipline = user?.trainingDiscipline ?: UserDiscipline.GYM.name
                        )
                        Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsSection("Perfil y objetivos") {
                    GoalPreferenceSelector(
                        selected = user?.goal?.substringBefore("|")?.trim().orEmpty(),
                        onSelected = { goal ->
                            viewModel.updatePrimaryGoal(goal)
                            showMessage("Meta actualizada: $goal.")
                        }
                    )
                    HorizontalDivider()
                    SettingsActionItem(
                        title = "Editar perfil",
                        description = "Actualiza tu nombre, objetivo y datos base.",
                        icon = Icons.Default.FitnessCenter,
                        onClick = {
                            viewModel.restartOnboarding()
                            onNavigate(Destinations.ONBOARDING)
                        }
                    )
                }
            }

            item {
                SettingsSection("Disciplina principal") {
                    DisciplinePreferenceSelector(
                        selected = selectedDiscipline,
                        onSelected = { discipline ->
                            viewModel.updateTrainingDiscipline(discipline)
                            showMessage("Disciplina actualizada a ${discipline.label}.")
                        }
                    )
                }
            }

            if (selectedDiscipline == UserDiscipline.HYBRID) {
                item {
                    SettingsSection("Ajustes modo hibrido") {
                        HybridSettingsPanel(
                            settings = hybridSettings,
                            onChange = { next ->
                                viewModel.updateHybridPlanSettings(next)
                                showMessage("Ajustes hibridos actualizados.")
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection("Notificaciones") {
                    SettingsToggleItem(
                        title = "Recordatorios inteligentes",
                        description = "Avisos suaves para check-in, agua y metas pendientes.",
                        icon = Icons.Default.Notifications,
                        checked = settings.remindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !canUseNotifications()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                return@SettingsToggleItem
                            }
                            if (enabled) {
                                enableReminders()
                            } else {
                                viewModel.setRemindersEnabled(false)
                                com.example.reminder.ReminderScheduler.cancelAllReminders(context)
                                showMessage("Recordatorios desactivados.")
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection("Preferencias visuales") {
                    ThemeSelectorRow(
                        activeTheme = activeTheme,
                        onThemeSelected = {
                            viewModel.updateThemeSelector(it)
                            showMessage("Tema actualizado.")
                        }
                    )
                    HorizontalDivider()
                    SettingsToggleItem(
                        title = "Usar sistema métrico",
                        description = "Muestra peso y medidas en kg/cm.",
                        icon = Icons.Default.Straighten,
                        checked = useMetric,
                        onCheckedChange = {
                            viewModel.toggleUnitsPreference()
                            showMessage("Unidades actualizadas.")
                        }
                    )
                }
            }

            item {
                SettingsSection("Rendimiento") {
                    SettingsActionItem(
                        title = "Modo rápido y diseño",
                        description = "Ajusta animaciones y gráficos según tu celular.",
                        icon = Icons.Default.Speed,
                        onClick = { onNavigate(Destinations.PERFORMANCE_SETTINGS) }
                    )
                    HorizontalDivider()
                    SettingsToggleItem(
                        title = "Automático según mi celular",
                        description = "FitPlan decide la calidad visual recomendada.",
                        icon = Icons.Default.Speed,
                        checked = performancePreferences.selectedMode == AppPerformanceMode.AUTO,
                        onCheckedChange = {
                            viewModel.updatePerformanceMode(
                                if (it) AppPerformanceMode.AUTO else AppPerformanceMode.BALANCED
                            )
                            showMessage(if (it) "Modo automático activado." else "Modo equilibrado activado.")
                        }
                    )
                    HorizontalDivider()
                    SettingsToggleItem(
                        title = "Más fluido",
                        description = "Reduce efectos visuales para celulares básicos.",
                        icon = Icons.Default.WaterDrop,
                        checked = performancePreferences.selectedMode == AppPerformanceMode.LIGHTWEIGHT,
                        onCheckedChange = {
                            viewModel.updatePerformanceMode(
                                if (it) AppPerformanceMode.LIGHTWEIGHT else AppPerformanceMode.AUTO
                            )
                            showMessage(if (it) "Modo rápido activado." else "Modo automático activado.")
                        }
                    )
                }
            }

            item {
                SettingsSection("Premium") {
                    SettingsActionItem(
                        title = if (isPremium) "Plan Premium activo" else "Activar Premium",
                        description = if (isPremium) "Tu suscripcion Premium esta activa." else "Pago seguro con Google Play Billing.",
                        icon = Icons.Default.Stars,
                        onClick = { onNavigate(Destinations.PREMIUM) }
                    )
                }
            }

            item {
                SettingsSection("Agua e hidratacion") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vaso estandar", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Se usa para el boton rapido de agua.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(250, 350, 500, 750).forEach { ml ->
                                FilterChip(
                                    selected = defaultGlassMl == ml,
                                    onClick = {
                                        viewModel.updateDefaultGlassMl(ml)
                                        showMessage("Vaso estandar actualizado a ${ml}ml.")
                                    },
                                    label = { Text("${ml}ml") }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection("Privacidad y datos") {
                    SettingsActionItem(
                        title = "Ver aviso de salud",
                        description = "Lee cómo usar FitPlan de forma responsable.",
                        icon = Icons.Default.Info,
                        onClick = { showHealthNotice = true }
                    )
                    HorizontalDivider()
                    SettingsActionItem(
                        title = "Exportar resumen",
                        description = "Genera un PDF formal para compartir con tu medico o nutriologo.",
                        icon = Icons.Default.Download,
                        onClick = { exportLocalSummary() }
                    )
                    HorizontalDivider()
                    SettingsActionItem(
                        title = "Limpiar datos locales",
                        description = "Elimina perfil, registros y progreso de este dispositivo.",
                        icon = Icons.Default.Delete,
                        tint = AlertRed,
                        onClick = { showDeleteDialog = true }
                    )
                }
            }

            item {
                SettingsSection("Ayuda") {
                    SettingsInfoItem(
                        title = "Cómo funciona tu inicio",
                        description = "FitPlan decide qué tarjetas mostrar según tu objetivo, plan, permisos y registros. Tú sólo ajustas preferencias seguras.",
                        icon = Icons.AutoMirrored.Filled.Help
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        viewModel.resetPerformanceSettings()
                        showMessage("Ajustes recomendados restaurados.")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restaurar ajustes recomendados", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "FitPlan MX v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = (if (BuildConfig.DEBUG) {
                        Modifier.clickable {
                            versionTapCount += 1
                            if (versionTapCount >= 7) {
                                versionTapCount = 0
                                onNavigate(Destinations.DEVELOPER_SETTINGS)
                            }
                        }
                    } else {
                        Modifier
                    })
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun GoalPreferenceSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val goals = listOf("Bajar grasa", "Ganar musculo", "Mantener peso", "Correr mas", "Mejorar condicion", "Bienestar / habitos")
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Meta principal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        goals.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { goal ->
                    FilterChip(
                        selected = selected.equals(goal, ignoreCase = true),
                        onClick = { onSelected(goal) },
                        label = { Text(goal, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DisciplinePreferenceSelector(
    selected: UserDiscipline,
    onSelected: (UserDiscipline) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserDiscipline.entries.forEach { discipline ->
            val isSelected = discipline == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(discipline) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(discipline.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            discipline.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HybridSettingsPanel(
    settings: HybridPlanSettings,
    onChange: (HybridPlanSettings) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Distribucion semanal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        SettingStepperRow("Fuerza por semana", settings.strengthDaysPerWeek, 0..7) {
            onChange(settings.copy(strengthDaysPerWeek = it))
        }
        SettingStepperRow("Cardio por semana", settings.cardioDaysPerWeek, 0..7) {
            onChange(settings.copy(cardioDaysPerWeek = it))
        }
        Text("Objetivo principal", style = MaterialTheme.typography.labelMedium)
        WrapFilterChips(
            options = listOf("Fuerza", "Resistencia", "Recomposicion", "Perdida de grasa"),
            selected = settings.objective,
            onSelected = { onChange(settings.copy(objective = it)) }
        )
        Text("Nivel", style = MaterialTheme.typography.labelMedium)
        WrapFilterChips(
            options = listOf("Principiante", "Intermedio", "Avanzado"),
            selected = settings.level,
            onSelected = { onChange(settings.copy(level = it)) }
        )
        Text("Duracion por sesion", style = MaterialTheme.typography.labelMedium)
        WrapFilterChips(
            options = listOf("30", "45", "60", "75"),
            selected = settings.sessionMinutes.toString(),
            onSelected = { onChange(settings.copy(sessionMinutes = it.toIntOrNull() ?: settings.sessionMinutes)) },
            suffix = " min"
        )
        HorizontalDivider()
        HybridToggleRow("Carrera", settings.runningEnabled) { onChange(settings.copy(runningEnabled = it)) }
        HybridToggleRow("Caminata", settings.walkingEnabled) { onChange(settings.copy(walkingEnabled = it)) }
        HybridToggleRow("Bici", settings.cyclingEnabled) { onChange(settings.copy(cyclingEnabled = it)) }
    }
}

@Composable
private fun SettingStepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("-")
            }
            Text(value.toString(), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("+")
            }
        }
    }
}

@Composable
private fun WrapFilterChips(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    suffix: String = ""
) {
    options.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option + suffix, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HybridToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSection(
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
            Column(content = content)
        }
    }
}

@Composable
private fun ThemeSelectorRow(activeTheme: String, onThemeSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tema visual", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("Elige automático, claro u oscuro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = activeTheme == "system", onClick = { onThemeSelected("system") }, label = { Text("Auto") })
            FilterChip(selected = activeTheme == "light", onClick = { onThemeSelected("light") }, label = { Text("Claro") })
            FilterChip(selected = activeTheme == "dark", onClick = { onThemeSelected("dark") }, label = { Text("Oscuro") })
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsItemLabel(title, description, icon, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsItemLabel(title, description, icon, Modifier.weight(1f), tint)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsInfoItem(
    title: String,
    description: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsItemLabel(title, description, icon, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsItemLabel(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (tint == AlertRed) AlertRed else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

