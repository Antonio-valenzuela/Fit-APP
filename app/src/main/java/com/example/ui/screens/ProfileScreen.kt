package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserDiscipline
import com.example.ui.theme.AlertRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.viewmodel.FitPlanViewModel

@Composable
fun ProfileScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val fitCoreAdvice by viewModel.fitCoreAdvice.collectAsStateWithLifecycle()
    val fitCoreLoading by viewModel.fitCoreLoading.collectAsStateWithLifecycle()
    val activeTheme by viewModel.themeSelector.collectAsStateWithLifecycle()
    val discipline = remember(user?.trainingDiscipline) { UserDiscipline.fromStoredName(user?.trainingDiscipline) }
    val isDark = LocalThemeIsDark.current

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.frostedMeshBackground(isDark),
        bottomBar = { FitPlanBottomBar(Destinations.PROFILE, onNavigate, isPremium, discipline) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Mi perfil",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (user?.name?.take(1) ?: "U").uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            user?.name ?: "Cargando...",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            user?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPremium) Icons.Default.Stars else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isPremium) GoldenAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isPremium) "Plan Premium" else "Plan gratuito",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPremium) GoldenAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text("FitCore AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Coach personalizado",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Plan, hábitos y recuperación con tus datos reales.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitCoreActionButton(
                            text = "Plan",
                            icon = Icons.Default.FitnessCenter,
                            enabled = !fitCoreLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.fetchFitCoreAdvice("plan de entrenamiento") }
                        )
                        FitCoreActionButton(
                            text = "Nutrición",
                            icon = Icons.Default.RestaurantMenu,
                            enabled = !fitCoreLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.fetchFitCoreAdvice("nutrición y hábitos") }
                        )
                        FitCoreActionButton(
                            text = "Recuperar",
                            icon = Icons.Default.Spa,
                            enabled = !fitCoreLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.fetchFitCoreAdvice("recuperación y bienestar") }
                        )
                    }

                    if (fitCoreLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "FitCore está analizando tus registros...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            fitCoreAdvice ?: "Elige una acción para recibir una recomendación clara y segura. Si faltan datos, FitCore te pedirá lo mínimo necesario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Text("Ajustes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleUnitsPreference() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usar sistema métrico (kg/cm)")
                        Switch(checked = useMetric, onCheckedChange = { viewModel.toggleUnitsPreference() })
                    }
                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tema de la app", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Elige automático, claro u oscuro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = activeTheme == "system",
                                onClick = { viewModel.updateThemeSelector("system") },
                                label = { Text("Auto", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = activeTheme == "light",
                                onClick = { viewModel.updateThemeSelector("light") },
                                label = { Text("Claro", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = activeTheme == "dark",
                                onClick = { viewModel.updateThemeSelector("dark") },
                                label = { Text("Oscuro", fontSize = 11.sp) }
                            )
                        }
                    }
                    HorizontalDivider()

                    ProfileNavRow(
                        text = "Ajustes de inicio y recordatorios",
                        onClick = { onNavigate(Destinations.SETTINGS) }
                    )
                    HorizontalDivider()

                    ProfileNavRow(
                        text = "Actualizar mis datos",
                        onClick = {
                            viewModel.restartOnboarding()
                            onNavigate(Destinations.ONBOARDING)
                        }
                    )
                }
            }

            Card(
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Privacidad y datos",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AlertRed
                    )
                    Text(
                        "Puedes revisar y limpiar tus datos locales desde Ajustes, con confirmación antes de borrar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onNavigate(Destinations.SETTINGS) },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_btn")
                    ) {
                        Text("Gestionar datos en Ajustes", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.logoutUser() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FitCoreActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileNavRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

