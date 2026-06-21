package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserDiscipline
import com.example.data.model.WellnessEntryDraft
import com.example.data.model.WellnessEntryRules
import com.example.data.model.WellnessEntryType
import com.example.ui.theme.AlertBlue
import com.example.ui.theme.AlertOrange
import com.example.ui.theme.AlertRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val logs by viewModel.observeTodayEntries().collectAsStateWithLifecycle()
    val workouts by viewModel.allWorkoutLogs.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val discipline = remember(user?.trainingDiscipline) { UserDiscipline.fromStoredName(user?.trainingDiscipline) }
    val defaultGlassMl by viewModel.defaultGlassMl.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val todayStart = remember { startOfTodayMillis() }
    val hasWorkoutToday = workouts.any { it.timestamp >= todayStart }
    val summary = WellnessEntryRules.buildTodaySummary(logs, hasWorkoutToday)

    var showEntryDialog by remember { mutableStateOf(false) }
    var choosingType by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<HealthLogEntity?>(null) }
    var selectedType by remember { mutableStateOf(WellnessEntryType.WATER) }
    var valueText by remember { mutableStateOf("") }
    var secondaryValueText by remember { mutableStateOf("") }
    var pulseText by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var sleepQuality by remember { mutableFloatStateOf(3f) }
    var energyValue by remember { mutableFloatStateOf(3f) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var deleteCandidate by remember { mutableStateOf<HealthLogEntity?>(null) }

    fun resetForm(type: WellnessEntryType = WellnessEntryType.WATER) {
        editingLog = null
        choosingType = false
        selectedType = type
        valueText = ""
        secondaryValueText = ""
        pulseText = ""
        contextText = defaultContextFor(type)
        noteText = ""
        sleepQuality = 3f
        energyValue = 3f
        errors = emptyList()
    }

    fun openTypeChooser() {
        resetForm()
        choosingType = true
        showEntryDialog = true
    }

    fun openCreate(type: WellnessEntryType = WellnessEntryType.WATER, presetValue: Int? = null, presetContext: String? = null) {
        resetForm(type)
        choosingType = false
        if (presetValue != null) valueText = presetValue.toString()
        if (presetContext != null) contextText = presetContext
        showEntryDialog = true
    }

    fun openEdit(log: HealthLogEntity) {
        val type = with(WellnessEntryRules) { log.resolvedType() }
        editingLog = log
        choosingType = false
        selectedType = type
        valueText = primaryValueFor(log, type)
        secondaryValueText = secondaryValueFor(log, type)
        pulseText = if (log.pulse > 0) log.pulse.toString() else ""
        contextText = log.context.ifBlank { defaultContextFor(type) }
        noteText = log.note.ifBlank { log.painFatigueNote }
        sleepQuality = if (log.sleepQualityScale > 0) log.sleepQualityScale.toFloat() else 3f
        energyValue = if (log.energyLevel > 0) log.energyLevel.toFloat() else 3f
        errors = emptyList()
        showEntryDialog = true
    }

    fun saveEntry() {
        val effectiveValue = when (selectedType) {
            WellnessEntryType.ENERGY -> energyValue.roundToInt().toString()
            else -> valueText
        }
        val effectiveSecondary = when (selectedType) {
            WellnessEntryType.SLEEP -> sleepQuality.roundToInt().toString()
            else -> secondaryValueText
        }
        val draft = WellnessEntryDraft(
            type = selectedType,
            valueText = effectiveValue,
            secondaryValueText = effectiveSecondary,
            unit = unitFor(selectedType),
            context = contextText,
            note = noteText,
            pulseText = pulseText
        )
        val validation = WellnessEntryRules.validate(draft)
        if (!validation.isValid) {
            errors = validation.errors
            return
        }
        val current = editingLog
        if (current == null) {
            viewModel.addWellnessEntry(draft)
        } else {
            viewModel.updateWellnessEntry(current, draft)
        }
        showEntryDialog = false
        resetForm()
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        bottomBar = { FitPlanBottomBar(Destinations.HEALTH, onNavigate, isPremium, discipline) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Diario de bienestar",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            summary.checkInText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { openTypeChooser() }) {
                        Text("Agregar entrada")
                    }
                }
            }

            item {
                TodaySummaryCard(summary = summary, onCreate = ::openCreate)
            }

            item {
                WaterQuickActions(
                    defaultGlassMl = defaultGlassMl,
                    onAdd = { amount, context -> openCreate(WellnessEntryType.WATER, amount, context) },
                    onQuickSave = viewModel::addWaterMl
                )
            }

            item {
                Text("Flujo de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (logs.isEmpty()) {
                item {
                EmptyWellnessState(onClick = { openTypeChooser() })
                }
            } else {
                items(logs.sortedByDescending { it.timestamp }, key = { it.id }) { log ->
                    WellnessEntryCard(
                        log = log,
                        onEdit = { openEdit(log) },
                        onDelete = { deleteCandidate = log }
                    )
                }
            }
        }
    }

    deleteCandidate?.let { log ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar entrada") },
            text = { Text("Esta accion quitara el registro del dia y recalculara el resumen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWellnessEntry(log)
                        deleteCandidate = null
                    }
                ) { Text("Eliminar", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") }
            }
        )
    }

    if (showEntryDialog) {
        ModalBottomSheet(onDismissRequest = { showEntryDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (editingLog == null) "Agregar entrada" else "Editar ${selectedType.label.lowercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (choosingType) {
                    EntryTypeChooser(
                        onSelect = {
                            selectedType = it
                            valueText = ""
                            secondaryValueText = ""
                            pulseText = ""
                            contextText = defaultContextFor(it)
                            noteText = ""
                            errors = emptyList()
                            choosingType = false
                        }
                    )
                    TextButton(onClick = { showEntryDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancelar")
                    }
                } else {
                    if (editingLog == null) {
                        TextButton(
                            onClick = { choosingType = true },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text("Cambiar tipo: ${selectedType.label}")
                        }
                    }

                    EntryFormFields(
                        type = selectedType,
                        valueText = valueText,
                        onValueText = { valueText = it },
                        secondaryValueText = secondaryValueText,
                        onSecondaryValueText = { secondaryValueText = it },
                        pulseText = pulseText,
                        onPulseText = { pulseText = it },
                        contextText = contextText,
                        onContextText = { contextText = it },
                        noteText = noteText,
                        onNoteText = { noteText = it },
                        sleepQuality = sleepQuality,
                        onSleepQuality = { sleepQuality = it },
                        energyValue = energyValue,
                        onEnergyValue = { energyValue = it }
                    )

                    if (errors.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                errors.forEach {
                                    Text(it, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showEntryDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancelar")
                        }
                        Button(onClick = ::saveEntry, modifier = Modifier.weight(1f)) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(summary: com.example.data.model.TodayWellnessSummary, onCreate: (WellnessEntryType) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Resumen de hoy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMiniWidget("Agua", "Agua: ${summary.waterMl} ml", summary.waterStatus, AlertBlue, Modifier.weight(1f)) { onCreate(WellnessEntryType.WATER) }
                HealthMiniWidget("Energia", summary.latestEnergyText, "Escala 1 a 5", GoldenAmber, Modifier.weight(1f)) { onCreate(WellnessEntryType.ENERGY) }
            }
            LinearProgressIndicator(
                progress = { summary.waterProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AlertBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMiniWidget("Sueno", summary.latestSleepText, "Descanso", AlertBlue, Modifier.weight(1f)) { onCreate(WellnessEntryType.SLEEP) }
                HealthMiniWidget("Pasos", summary.stepsText, "Movimiento diario", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { onCreate(WellnessEntryType.STEPS) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMiniWidget("Glucosa", summary.latestGlucoseText, "Ultima medicion", AlertOrange, Modifier.weight(1f)) { onCreate(WellnessEntryType.GLUCOSE) }
                HealthMiniWidget("Presion", summary.latestPressureText, "Ultima medicion", AlertRed, Modifier.weight(1f)) { onCreate(WellnessEntryType.PRESSURE) }
            }
        }
    }
}

@Composable
private fun WaterQuickActions(defaultGlassMl: Int, onAdd: (Int, String) -> Unit, onQuickSave: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Registro rapido de agua", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onQuickSave(defaultGlassMl) }, modifier = Modifier.weight(1f)) { Text("+ vaso ${defaultGlassMl} ml") }
                OutlinedButton(onClick = { onQuickSave(600) }, modifier = Modifier.weight(1f)) { Text("+ botella 600 ml") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onQuickSave(500) }, modifier = Modifier.weight(1f)) { Text("+ 500 ml") }
                OutlinedButton(onClick = { onQuickSave(1000) }, modifier = Modifier.weight(1f)) { Text("+ 1 litro") }
            }
            TextButton(onClick = { onAdd(defaultGlassMl, "vaso") }) { Text("Editar detalle antes de guardar") }
        }
    }
}

@Composable
private fun EmptyWellnessState(onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No tienes registros de bienestar hoy.", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Agrega agua, comida, sueno o una medicion para empezar tu seguimiento.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onClick) { Text("Agregar primera entrada") }
        }
    }
}

@Composable
private fun WellnessEntryCard(log: HealthLogEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val type = with(WellnessEntryRules) { log.resolvedType() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(entryIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(WellnessEntryRules.describeEntry(log), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar entrada")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar entrada", tint = AlertRed)
            }
        }
    }
}

@Composable
private fun EntryTypeChooser(onSelect: (WellnessEntryType) -> Unit) {
    val options = listOf(
        WellnessEntryType.WATER to "Vasos, botella o ml",
        WellnessEntryType.FOOD to "Porcion visual o calorias",
        WellnessEntryType.GLUCOSE to "mg/dL y contexto",
        WellnessEntryType.PRESSURE to "Sistolica, diastolica y pulso",
        WellnessEntryType.SLEEP to "Horas y calidad",
        WellnessEntryType.ENERGY to "Escala rapida 1 a 5",
        WellnessEntryType.STEPS to "Movimiento diario",
        WellnessEntryType.WEIGHT to "Peso corporal",
        WellnessEntryType.HABIT to "Check-in o habito"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Elige que quieres registrar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { (type, helper) ->
            Card(
                onClick = { onSelect(type) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(entryIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(type.label, fontWeight = FontWeight.SemiBold)
                        Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryFormFields(
    type: WellnessEntryType,
    valueText: String,
    onValueText: (String) -> Unit,
    secondaryValueText: String,
    onSecondaryValueText: (String) -> Unit,
    pulseText: String,
    onPulseText: (String) -> Unit,
    contextText: String,
    onContextText: (String) -> Unit,
    noteText: String,
    onNoteText: (String) -> Unit,
    sleepQuality: Float,
    onSleepQuality: (Float) -> Unit,
    energyValue: Float,
    onEnergyValue: (Float) -> Unit
) {
    when (type) {
        WellnessEntryType.WATER -> NumberField(valueText, onValueText, "Mililitros", "Ej. 250")
        WellnessEntryType.WEIGHT -> NumberField(valueText, onValueText, "Peso (kg)", "Ej. 72.5", KeyboardType.Decimal)
        WellnessEntryType.STEPS -> NumberField(valueText, onValueText, "Pasos", "Ej. 4500")
        WellnessEntryType.GLUCOSE -> {
            NumberField(valueText, onValueText, "Glucosa (mg/dL)", "Ej. 118")
            OptionRow(listOf("en ayunas", "antes de comer", "despues de comer", "antes de dormir", "otro"), contextText, onContextText)
        }
        WellnessEntryType.PRESSURE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(valueText, onValueText, "Sistolica", "120", modifier = Modifier.weight(1f))
                NumberField(secondaryValueText, onSecondaryValueText, "Diastolica", "80", modifier = Modifier.weight(1f))
            }
            NumberField(pulseText, onPulseText, "Pulso opcional", "72")
        }
        WellnessEntryType.SLEEP -> {
            NumberField(valueText, onValueText, "Horas dormidas", "7.5", KeyboardType.Decimal)
            Text("Calidad: ${sleepQuality.roundToInt()}/5", style = MaterialTheme.typography.labelSmall)
            Slider(value = sleepQuality, onValueChange = onSleepQuality, valueRange = 1f..5f, steps = 3)
        }
        WellnessEntryType.ENERGY -> {
            Text("Energia: ${energyValue.roundToInt()}/5", style = MaterialTheme.typography.labelSmall)
            Slider(value = energyValue, onValueChange = onEnergyValue, valueRange = 1f..5f, steps = 3)
        }
        WellnessEntryType.FOOD -> {
            OptionRow(listOf("Snack / colacion", "Medio plato", "Plato normal", "Plato grande"), contextText, onContextText)
            NumberField(valueText, onValueText, "Calorias opcionales", "Ej. 420")
        }
        WellnessEntryType.HABIT -> {
            OutlinedTextField(value = contextText, onValueChange = onContextText, label = { Text("Habito o check-in") }, modifier = Modifier.fillMaxWidth())
        }
    }
    OutlinedTextField(
        value = noteText,
        onValueChange = onNoteText,
        label = { Text("Nota opcional") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
    )
}

@Composable
private fun OptionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HealthMiniWidget(
    label: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
            modifier = modifier
        ) { content() }
    } else {
        Card(
            border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
            modifier = modifier
        ) { content() }
    }
}

private fun entryIcon(type: WellnessEntryType): ImageVector = when (type) {
    WellnessEntryType.WATER -> Icons.Default.WaterDrop
    WellnessEntryType.FOOD -> Icons.Default.Restaurant
    WellnessEntryType.WEIGHT -> Icons.Default.Scale
    WellnessEntryType.GLUCOSE, WellnessEntryType.PRESSURE -> Icons.Default.MonitorHeart
    WellnessEntryType.SLEEP -> Icons.Default.Bedtime
    WellnessEntryType.ENERGY -> Icons.Default.ElectricBolt
    WellnessEntryType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    WellnessEntryType.HABIT -> Icons.Default.FitnessCenter
}

private fun primaryValueFor(log: HealthLogEntity, type: WellnessEntryType): String = when (type) {
    WellnessEntryType.WATER -> log.waterIntakeMl.coerceAtLeast(log.value.roundToInt()).positiveString()
    WellnessEntryType.GLUCOSE -> log.glucoseLevel.coerceAtLeast(log.value).positiveString()
    WellnessEntryType.PRESSURE -> log.systolicPressure.coerceAtLeast(log.value.roundToInt()).positiveString()
    WellnessEntryType.SLEEP -> log.sleepDurationHours.coerceAtLeast(log.value).positiveString()
    WellnessEntryType.ENERGY -> log.energyLevel.coerceAtLeast(log.value.roundToInt()).positiveString()
    WellnessEntryType.STEPS -> log.steps.coerceAtLeast(log.value.roundToInt()).positiveString()
    WellnessEntryType.WEIGHT -> log.weightKg.coerceAtLeast(log.value).positiveString()
    WellnessEntryType.FOOD -> if (log.value > 0f) log.value.roundToInt().toString() else ""
    WellnessEntryType.HABIT -> log.value.positiveString()
}

private fun secondaryValueFor(log: HealthLogEntity, type: WellnessEntryType): String = when (type) {
    WellnessEntryType.PRESSURE -> log.diastolicPressure.coerceAtLeast(log.secondaryValue.roundToInt()).positiveString()
    WellnessEntryType.SLEEP -> log.sleepQualityScale.coerceAtLeast(log.secondaryValue.roundToInt()).positiveString()
    else -> if (log.secondaryValue > 0f) log.secondaryValue.roundToInt().toString() else ""
}

private fun defaultContextFor(type: WellnessEntryType): String = when (type) {
    WellnessEntryType.WATER -> "vaso"
    WellnessEntryType.FOOD -> "Plato normal"
    WellnessEntryType.GLUCOSE -> "antes de comer"
    WellnessEntryType.HABIT -> "check-in diario"
    else -> ""
}

private fun unitFor(type: WellnessEntryType): String = when (type) {
    WellnessEntryType.WATER -> "ml"
    WellnessEntryType.FOOD -> "kcal"
    WellnessEntryType.WEIGHT -> "kg"
    WellnessEntryType.GLUCOSE -> "mg/dL"
    WellnessEntryType.PRESSURE -> "mmHg"
    WellnessEntryType.SLEEP -> "h"
    WellnessEntryType.ENERGY -> "escala"
    WellnessEntryType.STEPS -> "pasos"
    WellnessEntryType.HABIT -> "check-in"
}

private fun startOfTodayMillis(): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun Int.positiveString(): String = if (this > 0) toString() else ""
private fun Float.positiveString(): String = if (this > 0f) {
    if (this % 1f == 0f) roundToInt().toString() else toString()
} else {
    ""
}
