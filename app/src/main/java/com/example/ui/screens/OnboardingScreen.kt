package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.analytics.ProductAnalytics
import com.example.data.model.UserDiscipline
import com.example.ui.theme.AlertRed
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.theme.glassCard
import com.example.ui.viewmodel.FitPlanViewModel

@Composable
fun OnboardingScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var step by remember { mutableStateOf(1) }
    var age by remember { mutableStateOf("26") }
    var weight by remember { mutableStateOf("70") }
    var height by remember { mutableStateOf("170") }
    var gender by remember { mutableStateOf("Masculino") }
    var activityLevel by remember { mutableStateOf("Moderado") }
    var goal by remember { mutableStateOf("Salud general") }
    var selectedDiscipline by remember { mutableStateOf(UserDiscipline.GYM) }
    var level by remember { mutableStateOf("Principiante") }
    var trainingDays by remember { mutableStateOf("3") }
    var trainingPlace by remember { mutableStateOf("Casa y gimnasio") }
    var equipment by remember { mutableStateOf("Peso corporal") }
    var intensity by remember { mutableStateOf("Moderada") }
    var weeklyGoal by remember { mutableStateOf("Completar 3 entrenamientos") }
    var limitations by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ProductAnalytics.track("onboarding_started")
    }
    var disclaimerAccepted by remember { mutableStateOf(false) }

    val isDark = LocalThemeIsDark.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .frostedMeshBackground(isDark)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(1, 2, 3, 4).forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (step >= index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                1 -> {
                    Text(
                        text = "Cuéntanos de ti",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Usaremos estos datos para calcular metas iniciales de comida y entrenamiento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 24.dp, isDark = isDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Age
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it },
                                label = { Text("Edad") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Weight
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("Peso actual (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Height
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it },
                                label = { Text("Estatura (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Gender Selector
                            Text(
                                text = "Sexo",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Masculino", "Femenino").forEach { g ->
                                    val isSelected = gender == g
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { gender = g },
                                        label = { Text(g) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val aNum = age.toIntOrNull() ?: 0
                            val wNum = weight.toFloatOrNull() ?: 0f
                            val hNum = height.toFloatOrNull() ?: 0f
                            if (aNum in 10..100 && wNum in 30f..250f && hNum in 100f..250f) {
                                step = 2
                            } else {
                                Toast.makeText(context, "Revisa edad, peso y estatura antes de continuar", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Siguiente")
                    }
                }

                2 -> {
                    Text(
                        text = "Tu objetivo",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Elige una meta realista. Puedes cambiarla después desde tu perfil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 24.dp, isDark = isDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Activity Label
                            Text(
                                text = "Enfoque principal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            UserDiscipline.entries.forEach { discipline ->
                                val selected = selectedDiscipline == discipline
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedDiscipline = discipline
                                            ProductAnalytics.track("discipline_selected", mapOf("discipline" to discipline.name))
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                discipline.label,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                discipline.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Actividad semanal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            listOf(
                                "Sedentario" to "Poco movimiento durante la semana",
                                "Ligero" to "Actividad ligera 1-3 días por semana",
                                "Moderado" to "Entrenas 3-5 días por semana",
                                "Activo" to "Entrenas casi todos los días"
                            ).forEach { (act, desc) ->
                                val selected = activityLevel == act
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { activityLevel = act }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(act, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Goals Label
                            Text(
                                text = "Meta principal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Bajar grasa", "Ganar musculo", "Mantener peso", "Correr mas", "Mejorar condicion", "Bienestar / habitos").forEach { g ->
                                    val sel = goal == g
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                goal = g
                                                ProductAnalytics.track("goal_selected", mapOf("goal" to g))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                g,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (sel) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f)) { Text("Atrás") }
                        Button(onClick = { step = 3 }, modifier = Modifier.weight(1.5f)) { Text("Siguiente") }
                    }
                }

                3 -> {
                    Text(
                        text = "Tu plan semanal",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "FitPlan usará esto para crear una experiencia guiada, sin que tengas que configurar widgets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 24.dp, isDark = isDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Nivel", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Principiante", "Intermedio", "Avanzado").forEach { option ->
                                    FilterChip(
                                        selected = level == option,
                                        onClick = { level = option },
                                        label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text("Días disponibles por semana", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("2", "3", "4", "5", "6").forEach { option ->
                                    FilterChip(
                                        selected = trainingDays == option,
                                        onClick = {
                                            trainingDays = option
                                            weeklyGoal = "Completar $option entrenamientos"
                                        },
                                        label = { Text(option) }
                                    )
                                }
                            }

                            Text("Lugar de entrenamiento", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Casa", "Gimnasio", "Ambos").forEach { option ->
                                    FilterChip(
                                        selected = trainingPlace == option,
                                        onClick = { trainingPlace = option },
                                        label = { Text(option) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = equipment,
                                onValueChange = { equipment = it },
                                label = { Text("Equipo disponible") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = intensity,
                                onValueChange = { intensity = it },
                                label = { Text("Intensidad") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = weeklyGoal,
                                onValueChange = { weeklyGoal = it },
                                label = { Text("Meta semanal") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                minLines = 1,
                                maxLines = 2
                            )

                            OutlinedTextField(
                                value = limitations,
                                onValueChange = { limitations = it },
                                label = { Text("Restricciones o molestias (opcional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 2 }, modifier = Modifier.weight(1f)) { Text("Atrás") }
                        Button(onClick = { step = 4 }, modifier = Modifier.weight(1.5f)) { Text("Siguiente") }
                    }
                }

                4 -> {
                    // CRITICAL COMPLIANCE WIZARD (APP STORE / GOOGLE PLAY LAWS)
                    Text(
                        text = "Uso responsable",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "FitPlan te ayuda a registrar hábitos, pero no reemplaza una consulta médica.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, AlertRed),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Aviso de salud",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AlertRed
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(180.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = """
                                    FitPlan MX es una herramienta para registrar hábitos, comida, entrenamiento y bienestar. 
                                    
                                    1. No sustituye al médico: las metas de calorías, macronutrientes, hábitos y consejos son estimaciones generales, no diagnósticos ni tratamientos.
                                    
                                    2. Si tienes hipertensión, diabetes, una condición cardiaca, embarazo u otra situación médica, sigue siempre la indicación de tu profesional de salud.
                                    
                                    3. Los consejos inteligentes pueden equivocarse. No tomes medicamentos, suplementos ni decisiones médicas basándote solo en esta app.
                                    
                                    Al continuar, confirmas que entiendes estos límites y usarás FitPlan como apoyo de registro personal.
                                    """.trimIndent(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { disclaimerAccepted = !disclaimerAccepted }
                            ) {
                                Checkbox(
                                    checked = disclaimerAccepted,
                                    onCheckedChange = { disclaimerAccepted = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Entiendo que FitPlan es una herramienta de apoyo y no reemplaza atención médica.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 3 }, modifier = Modifier.weight(1f)) { Text("Atrás") }
                        Button(
                            onClick = {
                                if (disclaimerAccepted) {
                                    val uName = user?.name ?: "Usuario FitPlan"
                                    viewModel.completeOnboarding(
                                        name = uName,
                                        age = age.toIntOrNull() ?: 25,
                                        weightKg = weight.toFloatOrNull() ?: 70.0f,
                                        heightCm = height.toFloatOrNull() ?: 170.0f,
                                        gender = gender,
                                        activityLevel = activityLevel,
                                        goal = "$goal | Nivel: $level | Días: $trainingDays | Lugar: $trainingPlace | Equipo: $equipment | Intensidad: $intensity | Meta: $weeklyGoal | Restricciones: ${limitations.ifBlank { "Ninguna" }}",
                                        disclaimerAccepted = true,
                                        trainingDiscipline = selectedDiscipline.name
                                    )
                                    onNavigate(Destinations.DASHBOARD)
                                } else {
                                    Toast.makeText(context, "Acepta el aviso de salud para continuar", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = disclaimerAccepted,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("finish_onboarding_btn")
                        ) {
                            Text("Empezar en FitPlan")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

