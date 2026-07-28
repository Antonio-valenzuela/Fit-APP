package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.billing.PremiumBillingManager
import com.example.data.billing.PremiumOffer
import com.example.data.analytics.ProductAnalytics
import com.example.data.model.PremiumAccessPolicy
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.theme.glassCard
import com.example.ui.viewmodel.FitPlanViewModel

@Composable
fun PremiumScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val isPremiumTestMode = PremiumAccessPolicy.isTestMode
    var offer by remember { mutableStateOf<PremiumOffer?>(null) }
    var billingReady by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isDark = LocalThemeIsDark.current
    val billingManager = remember(context) {
        PremiumBillingManager(
            context = context,
            onPremiumChanged = viewModel::setPremiumEntitlementFromBilling,
            onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        )
    }

    LaunchedEffect(isPremiumTestMode) {
        ProductAnalytics.track("paywall_seen", mapOf("surface" to "premium_screen"))
        if (!isPremiumTestMode) {
            billingManager.start { loadedOffer ->
                offer = loadedOffer
                billingReady = loadedOffer != null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { billingManager.end() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("premium_screen")
            .frostedMeshBackground(isDark)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(GoldenAmber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Stars, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(64.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "FitPlan Premium",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (isPremiumTestMode) {
                Surface(
                    modifier = Modifier.testTag("premium_test_mode_indicator"),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "Premium de prueba (QA)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Text(
                "Reportes avanzados, resumen semanal y ajustes automaticos para que tu plan se adapte a tus registros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 24.dp, isDark = isDark)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumFeatureRow(Icons.Default.AutoAwesome, "Coach avanzado", "El coach basico sigue disponible; Premium agrega resumen semanal y recomendaciones mas profundas.")
                    PremiumFeatureRow(Icons.AutoMirrored.Filled.ShowChart, "Reportes avanzados", "Tendencias de peso, energia, sueno, nutricion y entrenamiento.")
                    PremiumFeatureRow(Icons.Default.Stars, "Plan adaptativo", "Ajustes de volumen, calorias y recuperacion cuando tengas datos suficientes.")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Premium",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Muy pronto",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (isPremiumTestMode) {
                    "Modo QA local: no inicia Google Play Billing ni crea compras o suscripciones."
                } else if (billingReady) {
                    "Pago seguro gestionado por Google Play. Puedes cancelar desde tus suscripciones de Play."
                } else {
                    "Crea la suscripcion fitplan_premium_monthly en Play Console para activar cobros reales."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isPremium) {
                Button(
                    onClick = {
                        ProductAnalytics.track("premium_purchase_tapped", mapOf("surface" to "premium_screen"))
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "No se pudo abrir Google Play Billing.", Toast.LENGTH_LONG).show()
                        } else {
                            billingManager.launchPurchase(activity)
                        }
                    },
                    enabled = billingReady,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        offer?.let { "Activar Premium - ${it.price}" } ?: "Premium no configurado",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPremiumTestMode) {
                                "Premium de prueba activo. No representa una compra ni crea una suscripcion."
                            } else {
                                "Tu plan Premium esta activo en este dispositivo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onNavigate(Destinations.PROFILE)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Ver consejos", fontSize = 11.sp) }

                OutlinedButton(
                    onClick = { onNavigate(Destinations.DASHBOARD) },
                    modifier = Modifier.weight(1f)
                ) { Text("Volver al Inicio", fontSize = 11.sp) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PremiumFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(24.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
