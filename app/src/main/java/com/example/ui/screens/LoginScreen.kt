package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.example.data.auth.GoogleAuthManager
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.theme.glassCard
import com.example.ui.viewmodel.FitPlanViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: FitPlanViewModel) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorEmail by remember { mutableStateOf(false) }
    var errorName by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isDark = LocalThemeIsDark.current
    val scope = rememberCoroutineScope()
    val googleAuthManager = remember(context) { GoogleAuthManager(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .frostedMeshBackground(isDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header Image/Logo Setup
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "FitPlan MX Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "FitPlan MX",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Medicina Preventiva, Fitness y Nutrición Inteligente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Inputs card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 24.dp, isDark = isDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Iniciar Sesión de Bienestar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorName = false
                        },
                        label = { Text("Nombre Completo") },
                        isError = errorName,
                        supportingText = { if (errorName) Text("Ingresa tu nombre") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorEmail = false
                        },
                        label = { Text("Correo Electrónico") },
                        isError = errorEmail,
                        supportingText = { if (errorEmail) Text("Ingresa un correo válido") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                errorName = true
                            }
                            if (email.trim().isEmpty() || !email.contains("@")) {
                                errorEmail = true
                            }
                            if (!errorName && !errorEmail) {
                                viewModel.loginUser(email.trim(), name.trim())
                                Toast.makeText(context, "Sesión iniciada con éxito", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            "Comenzar Mi Ruta de Salud",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            googleLoading = true
                            scope.launch {
                                val result = googleAuthManager.signIn()
                                googleLoading = false
                                result
                                    .onSuccess { profile ->
                                        viewModel.loginUser(profile.email, profile.name)
                                        Toast.makeText(context, "Sesión iniciada con Google", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.localizedMessage ?: "No se pudo iniciar con Google",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        },
                        enabled = !googleLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (googleLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Continuar con Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

