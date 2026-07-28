package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import com.example.data.model.CalorieBalanceRules
import com.example.data.model.FoodDiaryRules
import com.example.data.model.FoodEntryDraft
import com.example.data.model.FoodItemEntity
import com.example.data.model.FoodValidationResult
import com.example.data.model.NutritionLabelParser
import com.example.data.model.ParsedNutritionLabel
import com.example.data.model.PremiumAccessPolicy
import com.example.data.ocr.NutritionLabelOcr
import com.example.ui.theme.AlertRed
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.theme.glassCard
import com.example.ui.viewmodel.FitPlanViewModel
import com.example.data.model.UserDiscipline
import com.example.data.api.GeminiImageRequest
import com.example.ui.nutrition.GeminiFoodPhase

@Composable
fun NutritionScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val legacyItems by viewModel.todayFoodLogs.collectAsStateWithLifecycle()
    val entries by viewModel.todayFoodEntries.collectAsStateWithLifecycle()
    val recentEntries by viewModel.recentFoodEntries.collectAsStateWithLifecycle()
    val favoriteFoods by viewModel.favoriteFoodItems.collectAsStateWithLifecycle()
    val allFoodItems by viewModel.allFoodItems.collectAsStateWithLifecycle()
    val workouts by viewModel.allWorkoutLogs.collectAsStateWithLifecycle()
    val sessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val todayHealth by viewModel.todayHealthLogs.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val geminiState by viewModel.geminiFoodUiState.collectAsStateWithLifecycle()
    val discipline = remember(user?.trainingDiscipline) { UserDiscipline.fromStoredName(user?.trainingDiscipline) }
    val context = LocalContext.current

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var foodName by rememberSaveable { mutableStateOf("") }
    var caloriesStr by rememberSaveable { mutableStateOf("") }
    var proteinStr by rememberSaveable { mutableStateOf("") }
    var carbsStr by rememberSaveable { mutableStateOf("") }
    var fatStr by rememberSaveable { mutableStateOf("") }
    var selectedMealType by rememberSaveable { mutableStateOf("Desayuno") }
    var selectedPortion by rememberSaveable { mutableStateOf("Plato") }
    var quickNote by rememberSaveable { mutableStateOf("") }
    var saveAsFavorite by rememberSaveable { mutableStateOf(false) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var showFrequentPicker by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var barcodeStr by rememberSaveable { mutableStateOf("") }
    var ocrLoading by remember { mutableStateOf(false) }
    var ocrStatus by remember { mutableStateOf<String?>(null) }
    var nutritionLabelPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var parsedNutritionLabel by remember { mutableStateOf<ParsedNutritionLabel?>(null) }
    var showOcrReviewDialog by remember { mutableStateOf(false) }
    val aiLoading = geminiState.isLoading
    var aiStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var capturedPreview by remember { mutableStateOf<Bitmap?>(null) }
    // OCR remains screen-scoped; Gemini work lives in the ViewModel to survive recreation.
    var ocrJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showGeminiConsentDialog by rememberSaveable { mutableStateOf(false) }
    var geminiConsentChecked by rememberSaveable { mutableStateOf(false) }
    var showGeminiPremiumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(geminiState.phase) {
        if (geminiState.phase == GeminiFoodPhase.AWAITING_CONSENT) {
            showGeminiConsentDialog = true
        }
    }
    LaunchedEffect(geminiState.message) {
        geminiState.message?.let { aiStatus = it }
    }
    LaunchedEffect(geminiState.request) {
        val bytes = geminiState.request?.imageBytes
        if (capturedPreview == null && bytes != null && bytes.isNotEmpty()) {
            capturedPreview = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }
    LaunchedEffect(geminiState.estimate) {
        geminiState.estimate?.let { estimate ->
            foodName = estimate.name
            caloriesStr = estimate.calories.takeIf { it > 0 }?.toString() ?: ""
            proteinStr = estimate.proteinG.takeIf { it > 0f }?.cleanMacro() ?: ""
            carbsStr = estimate.carbsG.takeIf { it > 0f }?.cleanMacro() ?: ""
            fatStr = estimate.fatG.takeIf { it > 0f }?.cleanMacro() ?: ""
            selectedPortion = "Porcion"
            quickNote = "${estimate.portionEstimate}. Confianza: ${estimate.confidenceScore.cleanConfidence()}. ${estimate.notes}"
        }
    }

    val todayDateKey = remember { com.example.data.repository.LocalDateUtils.getTodayString() }
    val todayStart = remember(todayDateKey) { nutritionStartOfTodayMillis() }
    val todayWorkouts = remember(workouts, todayStart) { workouts.filter { it.timestamp >= todayStart } }
    val todaySessions = remember(sessions, todayStart) { sessions.filter { it.timestamp >= todayStart } }
    val balance = remember(legacyItems, entries, todayWorkouts, todaySessions, todayHealth) {
        CalorieBalanceRules.build(legacyItems, entries, todayWorkouts, todaySessions, todayHealth)
    }
    val totalCals = balance.consumedCalories
    val totalProtein = balance.proteinGrams
    val totalCarbs = balance.carbsGrams
    val totalFat = balance.fatGrams
    val remainingCals = ((user?.targetCalories ?: 2000) - totalCals).coerceAtLeast(0)

    val isDark = LocalThemeIsDark.current
    val scope = rememberCoroutineScope()
    val featureGeminiFoodScanEnabled = PremiumAccessPolicy.isTestMode
    val canUseGeminiFoodScan = PremiumAccessPolicy.canUseGeminiFoodScan(
        isPremiumActive = isPremium,
        featureEnabled = featureGeminiFoodScanEnabled
    )
    fun createFoodPhotoUri(): Uri {
        val dir = File(context.cacheDir, "food_photos").apply { mkdirs() }
        val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun createNutritionLabelPhotoUri(): Uri {
        val dir = File(context.cacheDir, "food_photos").apply { mkdirs() }
        val file = File(dir, "label_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun requestGeminiConsent(prepared: PreparedFoodImage) {
        viewModel.prepareGeminiFood(prepared.toGeminiImageRequest(), foodName)
        geminiConsentChecked = false
        showGeminiConsentDialog = true
        aiStatus = "Imagen lista: ${prepared.width}x${prepared.height}, ${prepared.bytes.size / 1024} KB. Confirma antes de enviarla a Gemini."
    }

    val foodPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = capturedPhotoUri
        if (!success || uri == null) {
            aiStatus = "No se recibio foto. Vuelve a intentarlo."
            return@rememberLauncherForActivityResult
        }
        aiStatus = "Foto recibida. Preparando imagen..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { prepareFoodImage(context, uri) }
            }.onSuccess { prepared ->
                capturedPreview = prepared.preview
                requestGeminiConsent(prepared)
            }.onFailure { error ->
                aiStatus = error.localizedMessage ?: "No se pudo leer la foto."
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            aiStatus = "Permiso concedido. Abriendo camara..."
            val uri = createFoodPhotoUri()
            capturedPhotoUri = uri
            foodPhotoLauncher.launch(uri)
        } else {
            aiStatus = "Permiso de camara no concedido. Puedes registrar manualmente."
            Toast.makeText(context, "Permiso de camara no concedido.", Toast.LENGTH_LONG).show()
        }
    }

    fun processNutritionLabelImage(uri: Uri) {
        // Cancel any in-flight OCR job before starting a new one
        ocrJob?.cancel()
        ocrLoading = true
        ocrStatus = "Leyendo etiqueta..."
        ocrJob = scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    NutritionLabelOcr.readText(context, uri)
                }
                NutritionLabelParser.parse(text)
            }.onSuccess { parsed ->
                ocrLoading = false
                if (!parsed.hasAnyNutritionValue()) {
                    parsedNutritionLabel = null
                    showOcrReviewDialog = false
                    ocrStatus = "Aún no hay datos para mostrar. Puedes escribir los datos manualmente."
                    showDialog = true
                } else {
                    parsedNutritionLabel = parsed
                    showOcrReviewDialog = true
                    ocrStatus = if (parsed.confidence < 0.5f) {
                        "Revisa los datos, algunos valores pueden estar incompletos."
                    } else {
                        "Etiqueta leida en este dispositivo. Revisa antes de guardar."
                    }
                }
            }.onFailure {
                ocrLoading = false
                ocrStatus = "No pudimos leer bien la tabla. Puedes corregir los datos manualmente."
                showDialog = true
            }
        }
    }

    val nutritionLabelCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = nutritionLabelPhotoUri
        if (success && uri != null) {
            processNutritionLabelImage(uri)
        } else {
            ocrStatus = "No se recibio foto. Puedes escribir los datos manualmente."
            showDialog = true
        }
    }

    val nutritionLabelGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            processNutritionLabelImage(uri)
        } else {
            ocrStatus = "No se selecciono imagen. Puedes escribir los datos manualmente."
        }
    }

    val nutritionLabelPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createNutritionLabelPhotoUri()
            nutritionLabelPhotoUri = uri
            nutritionLabelCameraLauncher.launch(uri)
        } else {
            ocrStatus = "Permiso de camara no concedido. Puedes elegir una imagen o capturar manualmente."
            showDialog = true
        }
    }

    fun startNutritionLabelOcrCamera() {
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) {
            ocrStatus = "Tu dispositivo no tiene camara disponible. Puedes escribir los datos manualmente."
            showDialog = true
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createNutritionLabelPhotoUri()
            nutritionLabelPhotoUri = uri
            nutritionLabelCameraLauncher.launch(uri)
        } else {
            nutritionLabelPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startFoodPhotoFlow() {
        if (!canUseGeminiFoodScan) {
            aiStatus = "Escaneo inteligente con IA estará disponible en la versión Premium. Puedes registrar la comida manualmente."
            showGeminiPremiumDialog = true
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            aiStatus = "Abriendo camara..."
            val uri = createFoodPhotoUri()
            capturedPhotoUri = uri
            foodPhotoLauncher.launch(uri)
        } else {
            aiStatus = "Solicitando permiso de camara..."
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun retryAnalysis() {
        if (!canUseGeminiFoodScan) {
            aiStatus = "Escaneo inteligente con IA estará disponible en la versión Premium. Puedes registrar la comida manualmente."
            showGeminiPremiumDialog = true
            return
        }
        if (geminiState.request == null) {
            aiStatus = "Primero toma una foto valida."
        } else {
            geminiConsentChecked = false
            showGeminiConsentDialog = true
            aiStatus = "La imagen preparada sigue disponible. Confirma para volver a enviarla."
        }
    }

    fun resetAiCapture() {
        viewModel.resetGeminiFoodForManual()
        aiStatus = "Registro manual activo. Puedes escribir los datos."
        capturedPhotoUri = null
        capturedPreview?.recycle()
        capturedPreview = null
        showGeminiConsentDialog = false
        geminiConsentChecked = false
    }

    fun normalizedMealType(label: String): String = when (label) {
        "Desayuno" -> "DESAYUNO"
        "Comida" -> "COMIDA"
        "Cena" -> "CENA"
        else -> "COLACION"
    }

    fun fillFromFoodItem(item: FoodItemEntity, source: String = "FAVORITE") {
        foodName = item.name
        caloriesStr = item.caloriesPerServing.takeIf { it > 0 }?.toString() ?: ""
        proteinStr = item.proteinGrams.takeIf { it > 0f }?.cleanMacro() ?: ""
        carbsStr = item.carbsGrams.takeIf { it > 0f }?.cleanMacro() ?: ""
        fatStr = item.fatGrams.takeIf { it > 0f }?.cleanMacro() ?: ""
        selectedPortion = item.servingLabel
        barcodeStr = item.barcode.orEmpty()
        quickNote = if (source == "BARCODE") "Producto guardado localmente." else "Cargado desde alimentos guardados."
        showFavoritePicker = false
        showFrequentPicker = false
        showDialog = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.testTag("nutrition_screen").frostedMeshBackground(isDark),
        bottomBar = { FitPlanBottomBar(Destinations.NUTRITION, onNavigate, isPremium, discipline) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Diario de Alimentación",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            NutritionPrimaryActions(
                ocrLoading = ocrLoading,
                onAddFood = { showDialog = true },
                onReadLabel = { startNutritionLabelOcrCamera() },
                onBarcode = { showBarcodeDialog = true },
                onFavorites = { showFavoritePicker = true },
                onFrequent = { showFrequentPicker = true }
            )

            ocrStatus?.let { status ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "FitPlan procesa la imagen en tu dispositivo para extraer datos nutrimentales. La imagen no se envia a servidores ni se usa para diagnostico medico.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!ocrLoading) {
                            TextButton(onClick = { nutritionLabelGalleryLauncher.launch("image/*") }) {
                                Text("Elegir imagen de galeria")
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // Balance Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 24.dp, isDark = isDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Presupuesto calórico de hoy", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${user?.targetCalories ?: 2000} kcal",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Consumido: $totalCals kcal", style = MaterialTheme.typography.titleMedium)
                            Text("Por consumir: $remainingCals kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Macros: P ${totalProtein.cleanMacro()}g | C ${totalCarbs.cleanMacro()}g | G ${totalFat.cleanMacro()}g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Quemadas registradas: ${balance.burnedCalories} kcal | Balance: ${balance.balanceCalories} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CalorieBalanceRules.disclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text("Comidas registradas hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (entries.isEmpty() && legacyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Aun no registras comida hoy.", color = MaterialTheme.colorScheme.onSurface)
                        Text("Puedes guardar solo nombre y porcion, sin bascula.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showDialog = true }) {
                            Text("Agregar primera comida")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries) { food ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        food.foodName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(food.mealType.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Text(
                                            "${food.portionLabel} | P: ${food.proteinGrams.cleanMacro()}g | C: ${food.carbsGrams.cleanMacro()}g | G: ${food.fatGrams.cleanMacro()}g",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (food.calories > 0) "${food.calories} kcal" else "Sin kcal",
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    IconButton(onClick = { viewModel.removeFoodEntry(food) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AlertRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue to register meals
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(16.dp)
                    .testTag("food_entry_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Registrar comida",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedButton(
                        onClick = { startFoodPhotoFlow() },
                        enabled = !aiLoading,
                        modifier = Modifier.fillMaxWidth().testTag("gemini_scan_button")
                    ) {
                        if (aiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analizando foto...")
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (canUseGeminiFoodScan) "Escanear comida con IA" else "Escaneo IA Premium",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    capturedPreview?.let { preview ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_phase_${geminiState.phase.name.lowercase()}")
                        ) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Foto tomada de la comida",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    aiStatus?.let { status ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { retryAnalysis() },
                            enabled = geminiState.canRetry,
                            modifier = Modifier.weight(1f).testTag("gemini_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Otra vez")
                        }
                        TextButton(
                            onClick = {
                                if (aiLoading) {
                                    viewModel.cancelGeminiFoodAnalysis()
                                } else {
                                    resetAiCapture()
                                }
                            },
                            enabled = true,
                            modifier = Modifier.weight(1f).testTag("gemini_cancel_or_manual_button")
                        ) {
                            Text(if (aiLoading) "Cancelar" else "Manual")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val local = viewModel.estimateFoodLocally(foodName)
                            if (local == null) {
                                aiStatus = "Escribe el nombre exacto de un alimento guardado o completa el registro manualmente."
                            } else {
                                foodName = local.name
                                caloriesStr = local.calories.toString()
                                proteinStr = local.proteinG.cleanMacro()
                                carbsStr = local.carbsG.cleanMacro()
                                fatStr = local.fatG.cleanMacro()
                                selectedPortion = local.portionEstimate
                                quickNote = local.notes
                                aiStatus = "Estimacion local aplicada sin enviar la imagen. Revisa los campos antes de guardar."
                            }
                        },
                        enabled = !aiLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Usar estimacion local")
                    }

                    OutlinedTextField(
                        value = foodName,
                        onValueChange = {
                            foodName = it
                            viewModel.updateGeminiFoodDescription(it)
                        },
                        label = { Text("Alimento o platillo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Porcion visual", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FoodDiaryRules.visualPortions.forEach { portion ->
                            FilterChip(
                                selected = selectedPortion == portion,
                                onClick = { selectedPortion = portion },
                                label = { Text(portion) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        label = { Text("Registro rapido opcional") },
                        supportingText = { Text("Ejemplo: tacos de pollo, vaso de leche, plato de arroz") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Tipo de comida", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Desayuno", "Comida", "Cena", "Colacion").forEach { type ->
                            FilterChip(
                                selected = selectedMealType == type,
                                onClick = { selectedMealType = type },
                                label = { Text(type) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it },
                        label = { Text("Calorías (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = proteinStr,
                            onValueChange = { proteinStr = it },
                            label = { Text("Prot. (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = carbsStr,
                            onValueChange = { carbsStr = it },
                            label = { Text("Carb. (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fatStr,
                            onValueChange = { fatStr = it },
                            label = { Text("Lipid. (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { saveAsFavorite = !saveAsFavorite },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = saveAsFavorite, onCheckedChange = { saveAsFavorite = it })
                        Text("Guardar tambien como favorito")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
                        Button(
                            onClick = {
                                val typedName = foodName.trim().ifBlank { quickNote.trim() }
                                val validation = viewModel.addFoodEntry(
                                    FoodEntryDraft(
                                        foodName = typedName,
                                        portionLabel = selectedPortion,
                                        mealType = normalizedMealType(selectedMealType),
                                        calories = caloriesStr.toIntOrNull(),
                                        proteinGrams = proteinStr.toFloatOrNull(),
                                        carbsGrams = carbsStr.toFloatOrNull(),
                                        fatGrams = fatStr.toFloatOrNull(),
                                        source = if (barcodeStr.isNotBlank()) "BARCODE" else "MANUAL",
                                        barcode = barcodeStr,
                                        notes = quickNote
                                    ),
                                    saveAsFavorite = saveAsFavorite
                                )
                                if (!validation.isValid) {
                                    Toast.makeText(context, validation.errors.firstOrNull() ?: "Revisa los campos antes de guardar.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDialog = false
                                    foodName = ""
                                    caloriesStr = ""
                                    proteinStr = ""
                                    carbsStr = ""
                                    fatStr = ""
                                    quickNote = ""
                                    barcodeStr = ""
                                    saveAsFavorite = false
                                    selectedPortion = "Plato"
                                    aiStatus = null
                                    capturedPhotoUri = null
                                    capturedPreview = null
                                    viewModel.resetGeminiFoodForManual()
                                }
                            },
                            modifier = Modifier.testTag("save_meal_button")
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }

    if (showGeminiPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showGeminiPremiumDialog = false },
            icon = { Icon(Icons.Default.Stars, contentDescription = null) },
            title = { Text("Función Premium") },
            text = {
                Text("Escaneo inteligente con IA estará disponible en la versión Premium. El registro manual de comida sigue gratis y no requiere cámara.")
            },
            confirmButton = {
                Button(onClick = { showGeminiPremiumDialog = false }) {
                    Text("Entendido")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGeminiPremiumDialog = false
                        showDialog = true
                    }
                ) {
                    Text("Registrar manual")
                }
            }
        )
    }

    if (showGeminiConsentDialog) {
        AlertDialog(
            modifier = Modifier.testTag("gemini_consent_dialog"),
            onDismissRequest = {
                showGeminiConsentDialog = false
                geminiConsentChecked = false
                viewModel.cancelGeminiFoodAnalysis()
                aiStatus = "Envio a Gemini cancelado. Puedes registrar la comida manualmente."
            },
            icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
            title = { Text("Confirmar analisis con Gemini") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(GeminiNutritionConsent.text)
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = geminiConsentChecked,
                            onCheckedChange = { geminiConsentChecked = it },
                            modifier = Modifier.testTag("gemini_consent_checkbox")
                        )
                        Text(
                            "Entiendo y acepto enviar esta imagen/datos a Gemini.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = geminiConsentChecked && geminiState.request != null,
                    modifier = Modifier.testTag("gemini_consent_send"),
                    onClick = {
                        showGeminiConsentDialog = false
                        geminiConsentChecked = false
                        viewModel.analyzePreparedGeminiFood()
                    }
                ) {
                    Text("Enviar a Gemini")
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("gemini_consent_cancel"),
                    onClick = {
                        showGeminiConsentDialog = false
                        geminiConsentChecked = false
                        viewModel.cancelGeminiFoodAnalysis()
                        aiStatus = "Envio a Gemini cancelado. Puedes registrar la comida manualmente."
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showFavoritePicker) {
        FoodItemPickerDialog(
            title = "Alimentos favoritos",
            emptyText = "Aun no tienes favoritos. Marca una comida como favorita al guardarla.",
            items = favoriteFoods,
            onDismiss = { showFavoritePicker = false },
            onSelect = { fillFromFoodItem(it) }
        )
    }

    if (showFrequentPicker) {
        val frequentEntries = remember(recentEntries) { FoodDiaryRules.getFrequentFoods(recentEntries).take(10) }
        val frequentItems = remember(frequentEntries) { frequentEntries.map { FoodDiaryRules.toFoodItem(it, false) } }
        FoodItemPickerDialog(
            title = "Comidas frecuentes",
            emptyText = "Tus comidas frecuentes apareceran despues de registrar algunos alimentos.",
            items = frequentItems,
            onDismiss = { showFrequentPicker = false },
            onSelect = { fillFromFoodItem(it, source = "TEMPLATE") }
        )
    }

    if (showBarcodeDialog) {
        AlertDialog(
            onDismissRequest = { showBarcodeDialog = false },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
            title = { Text("Codigo de barras opcional") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("El escáner no está disponible. Ingresa los dígitos del código de barras manualmente. Si no existe en tu caché local, podrás crearlo.")
                    OutlinedTextField(
                        value = barcodeStr,
                        onValueChange = { barcodeStr = it.filter(Char::isDigit) },
                        label = { Text("Codigo de barras") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cached = allFoodItems.firstOrNull { it.barcode == barcodeStr.trim() }
                        showBarcodeDialog = false
                        if (cached != null) {
                            fillFromFoodItem(cached, source = "BARCODE")
                        } else {
                            aiStatus = "Producto no encontrado. Puedes crearlo y guardarlo para la proxima."
                            showDialog = true
                        }
                    }
                ) { Text("Buscar local") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBarcodeDialog = false
                        showDialog = true
                    }
                ) { Text("Crear manual") }
            }
        )
    }

    // Use a local val to smart-cast safely and avoid NPE race between null-check and !! dereference
    val currentParsedLabel = parsedNutritionLabel
    if (showOcrReviewDialog && currentParsedLabel != null) {
        NutritionLabelReviewDialog(
            parsed = currentParsedLabel,
            initialBarcode = barcodeStr,
            onDismiss = { showOcrReviewDialog = false },
            onSaveFood = { item ->
                viewModel.saveFoodItem(item)
                ocrStatus = "Alimento guardado. Puedes usarlo desde frecuentes o favoritos."
                showOcrReviewDialog = false
            },
            onSaveAndRegister = { item, draft, favorite ->
                val validation = viewModel.addFoodEntry(draft, saveAsFavorite = favorite)
                if (validation.isValid) {
                    viewModel.saveFoodItem(item)
                }
                validation
            },
            onSavedAndRegistered = {
                ocrStatus = "Alimento guardado y registrado hoy."
                showOcrReviewDialog = false
            }
        )
    }
}

@Composable
private fun NutritionPrimaryActions(
    ocrLoading: Boolean,
    onAddFood: () -> Unit,
    onReadLabel: () -> Unit,
    onBarcode: () -> Unit,
    onFavorites: () -> Unit,
    onFrequent: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAddFood,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("primary_register_food_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registrar comida", fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onReadLabel,
                enabled = !ocrLoading,
                leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(if (ocrLoading) "Leyendo..." else "Leer tabla") }
            )
            AssistChip(
                onClick = onBarcode,
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Codigo") }
            )
            AssistChip(
                onClick = onFavorites,
                modifier = Modifier.testTag("open_favorites_button"),
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Favoritos") }
            )
            AssistChip(
                onClick = onFrequent,
                modifier = Modifier.testTag("open_frequent_button"),
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Frecuentes") }
            )
        }
    }
}

@Composable
private fun NutritionLabelReviewDialog(
    parsed: ParsedNutritionLabel,
    initialBarcode: String,
    onDismiss: () -> Unit,
    onSaveFood: (FoodItemEntity) -> Unit,
    onSaveAndRegister: (FoodItemEntity, FoodEntryDraft, Boolean) -> FoodValidationResult,
    onSavedAndRegistered: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(parsed) { mutableStateOf("") }
    var brand by remember(parsed) { mutableStateOf("") }
    var barcode by remember(parsed, initialBarcode) { mutableStateOf(initialBarcode) }
    var serving by remember(parsed) { mutableStateOf(parsed.servingSizeLabel ?: "Plato normal") }
    var calories by remember(parsed) { mutableStateOf(parsed.calories.toInputString()) }
    var protein by remember(parsed) { mutableStateOf(parsed.proteinGrams.toInputString()) }
    var carbs by remember(parsed) { mutableStateOf(parsed.carbsGrams.toInputString()) }
    var fat by remember(parsed) { mutableStateOf(parsed.fatGrams.toInputString()) }
    var saturatedFat by remember(parsed) { mutableStateOf(parsed.saturatedFatGrams.toInputString()) }
    var fiber by remember(parsed) { mutableStateOf(parsed.fiberGrams.toInputString()) }
    var sugar by remember(parsed) { mutableStateOf(parsed.sugarGrams.toInputString()) }
    var sodium by remember(parsed) { mutableStateOf(parsed.sodiumMg.toInputString()) }
    var notes by remember(parsed) { mutableStateOf("") }
    var favorite by remember(parsed) { mutableStateOf(false) }
    var mealType by remember(parsed) { mutableStateOf("DESAYUNO") }

    fun buildFoodItem(): FoodItemEntity {
        return FoodItemEntity(
            name = name.trim(),
            brand = brand.trim().takeIf { it.isNotBlank() },
            barcode = barcode.trim().takeIf { it.isNotBlank() },
            caloriesPerServing = calories.toDoubleInput()?.roundToInt() ?: 0,
            proteinGrams = protein.toFloatInput() ?: 0f,
            carbsGrams = carbs.toFloatInput() ?: 0f,
            fatGrams = fat.toFloatInput() ?: 0f,
            saturatedFatGrams = saturatedFat.toFloatInput(),
            fiberGrams = fiber.toFloatInput(),
            sugarGrams = sugar.toFloatInput(),
            sodiumMg = sodium.toFloatInput(),
            servingLabel = serving.ifBlank { "Plato normal" },
            isFavorite = favorite,
            isCustom = true
        )
    }

    fun buildDraft(): FoodEntryDraft {
        return FoodEntryDraft(
            foodName = name.trim(),
            portionLabel = serving.ifBlank { "Plato normal" },
            mealType = mealType,
            calories = calories.toDoubleInput()?.roundToInt(),
            proteinGrams = protein.toFloatInput(),
            carbsGrams = carbs.toFloatInput(),
            fatGrams = fat.toFloatInput(),
            saturatedFatGrams = saturatedFat.toFloatInput(),
            fiberGrams = fiber.toFloatInput(),
            sugarGrams = sugar.toFloatInput(),
            sodiumMg = sodium.toFloatInput(),
            source = "OCR",
            barcode = barcode,
            notes = notes
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
        title = { Text("Revisar datos detectados") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (parsed.confidence < 0.5f) {
                    Text(
                        "Revisa los datos, algunos valores pueden estar incompletos.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                parsed.warnings.forEach { warning ->
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "FitPlan proceso la imagen en tu dispositivo. No se envio a servidores.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del alimento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Marca opcional") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = barcode, onValueChange = { barcode = it.filter(Char::isDigit) }, label = { Text("Codigo de barras opcional") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = serving, onValueChange = { serving = it }, label = { Text("Tamano de porcion") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionNumberField(calories, { calories = it }, "Kcal", Modifier.weight(1f))
                    NutritionNumberField(protein, { protein = it }, "Proteina g", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionNumberField(carbs, { carbs = it }, "Carbos g", Modifier.weight(1f))
                    NutritionNumberField(fat, { fat = it }, "Grasas g", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionNumberField(fiber, { fiber = it }, "Fibra g", Modifier.weight(1f))
                    NutritionNumberField(sugar, { sugar = it }, "Azucar g", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionNumberField(sodium, { sodium = it }, "Sodio mg", Modifier.weight(1f))
                    NutritionNumberField(saturatedFat, { saturatedFat = it }, "Sat. g", Modifier.weight(1f))
                }
                Text("Tipo de comida si registras hoy", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DESAYUNO", "COMIDA", "CENA", "COLACION").forEach { type ->
                        FilterChip(selected = mealType == type, onClick = { mealType = type }, label = { Text(type.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth().clickable { favorite = !favorite }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = favorite, onCheckedChange = { favorite = it })
                    Text("Marcar como favorito")
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (name.trim().isBlank()) {
                            Toast.makeText(context, "Ingresa el nombre del alimento.", Toast.LENGTH_SHORT).show()
                        } else {
                            onSaveFood(buildFoodItem())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar alimento") }
                Button(
                    onClick = {
                        if (name.trim().isBlank()) {
                            Toast.makeText(context, "Ingresa el nombre del alimento.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (calories.isBlank() && protein.isBlank() && carbs.isBlank() && fat.isBlank()) {
                            Toast.makeText(context, "Puedes guardar parcial, pero revisa que los datos esten completos.", Toast.LENGTH_LONG).show()
                        }
                        val validation = onSaveAndRegister(buildFoodItem(), buildDraft(), favorite)
                        if (validation.isValid) {
                            onSavedAndRegistered()
                        } else {
                            Toast.makeText(context, validation.errors.firstOrNull() ?: "Revisa los campos antes de guardar.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar y registrar hoy") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun NutritionNumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValue(input.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        singleLine = true
    )
}

@Composable
private fun FoodItemPickerDialog(
    title: String,
    emptyText: String,
    items: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onSelect: (FoodItemEntity) -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag(
            if (title == "Alimentos favoritos") "favorites_dialog" else "frequent_dialog"
        ),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(emptyText)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        Card(
                            onClick = { onSelect(item) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.servingLabel} | ${item.caloriesPerServing.takeIf { it > 0 }?.toString() ?: "sin kcal"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

private fun estimateCaloriesFromPortion(portion: String, name: String): Int {
    val base = when {
        name.contains("ensalada", ignoreCase = true) -> 120
        name.contains("pollo", ignoreCase = true) || name.contains("atun", ignoreCase = true) -> 220
        name.contains("arroz", ignoreCase = true) || name.contains("pasta", ignoreCase = true) -> 260
        name.contains("taco", ignoreCase = true) -> 180
        name.contains("pan", ignoreCase = true) -> 160
        else -> 240
    }
    return when (portion) {
        "1 cucharada" -> (base * 0.2f).toInt()
        "1 pieza" -> (base * 0.75f).toInt()
        "1 taza" -> base
        "Porcion grande" -> (base * 1.5f).toInt()
        else -> (base * 1.1f).toInt()
    }
}

private fun estimateProteinFromPortion(portion: String, name: String): Float {
    val base = if (name.contains("pollo", true) || name.contains("atun", true) || name.contains("huevo", true)) 24f else 8f
    return base * portionFactor(portion)
}

private fun estimateCarbsFromPortion(portion: String, name: String): Float {
    val base = if (name.contains("arroz", true) || name.contains("pasta", true) || name.contains("pan", true) || name.contains("taco", true)) 32f else 18f
    return base * portionFactor(portion)
}

private fun estimateFatFromPortion(portion: String, name: String): Float {
    val base = if (name.contains("aguacate", true) || name.contains("nuez", true) || name.contains("queso", true)) 18f else 8f
    return base * portionFactor(portion)
}

private fun portionFactor(portion: String): Float = when (portion) {
    "1 cucharada" -> 0.2f
    "1 pieza" -> 0.75f
    "1 taza" -> 1f
    "Porcion grande" -> 1.5f
    else -> 1.1f
}

private const val FOOD_IMAGE_MAX_OUTPUT_BYTES = 4 * 1024 * 1024

internal data class FoodImageProfile(
    val id: String,
    val maxSidePx: Int,
    val jpegQuality: Int,
    val maxOutputBytes: Int = FOOD_IMAGE_MAX_OUTPUT_BYTES
) {
    init {
        require(id.isNotBlank())
        require(maxSidePx > 0)
        require(jpegQuality in 1..100)
        require(maxOutputBytes > 0)
    }
}

internal val CONSERVATIVE_FOOD_IMAGE_PROFILE = FoodImageProfile("conservative", 1600, 85)
internal val BALANCED_FOOD_IMAGE_PROFILE = FoodImageProfile("balanced", 1280, 75)
internal val AGGRESSIVE_FOOD_IMAGE_PROFILE = FoodImageProfile("aggressive", 960, 65)

internal data class PreparedFoodImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val preview: Bitmap,
    val originalBytes: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val quality: Int,
    val profileId: String,
    val mimeType: String = "image/jpeg"
)

internal object GeminiNutritionConsent {
    const val text: String =
        "Al continuar, aceptas enviar esta imagen/datos a Gemini para analisis. FitPlan no sustituye diagnóstico médico."
}

internal fun <T> useRequiredInputStream(
    openStream: () -> java.io.InputStream?,
    missingMessage: String,
    block: (java.io.InputStream) -> T
): T {
    val input = openStream() ?: throw IllegalStateException(missingMessage)
    return input.use(block)
}

internal fun prepareFoodImage(
    context: Context,
    uri: Uri,
    profile: FoodImageProfile = BALANCED_FOOD_IMAGE_PROFILE
): PreparedFoodImage {
    val originalBytes = contentLength(context, uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    useRequiredInputStream(
        openStream = { context.contentResolver.openInputStream(uri) },
        missingMessage = "No se pudo leer la foto tomada."
    ) { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalStateException("La foto no contiene dimensiones validas.")
    }

    val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
        runCatching {
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateDecodeSample(bounds.outWidth, bounds.outHeight, profile.maxSidePx)
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: throw IllegalStateException("No se pudo decodificar la foto tomada.")
    val oriented = applyExifOrientation(decoded, orientation)
    if (oriented !== decoded) decoded.recycle()

    val originalWidth = if (orientationSwapsDimensions(orientation)) bounds.outHeight else bounds.outWidth
    val originalHeight = if (orientationSwapsDimensions(orientation)) bounds.outWidth else bounds.outHeight
    val longestSide = max(oriented.width, oriented.height).coerceAtLeast(1)
    val scale = if (longestSide > profile.maxSidePx) {
        profile.maxSidePx.toFloat() / longestSide.toFloat()
    } else {
        1f
    }
    val targetWidth = (oriented.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (oriented.height * scale).roundToInt().coerceAtLeast(1)
    val resized = if (targetWidth != oriented.width || targetHeight != oriented.height) {
        Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true)
    } else {
        oriented
    }
    if (resized !== oriented) oriented.recycle()

    val bytes = ByteArrayOutputStream().use { output ->
        val compressed = resized.compress(Bitmap.CompressFormat.JPEG, profile.jpegQuality, output)
        if (!compressed) throw IllegalStateException("No se pudo comprimir la foto.")
        output.toByteArray()
    }
    if (bytes.isEmpty()) throw IllegalStateException("La foto comprimida quedo vacia.")
    if (bytes.size > profile.maxOutputBytes) {
        resized.recycle()
        throw IllegalStateException("La foto preparada es demasiado grande. Elige una imagen mas pequena.")
    }

    return PreparedFoodImage(
        bytes = bytes,
        width = resized.width,
        height = resized.height,
        preview = resized,
        originalBytes = originalBytes,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        quality = profile.jpegQuality,
        profileId = profile.id
    )
}

private fun contentLength(context: Context, uri: Uri): Int {
    val descriptorLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
    if (descriptorLength != null) return descriptorLength

    return context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > Int.MAX_VALUE) throw IllegalStateException("La foto original es demasiado grande.")
        }
        total.toInt()
    } ?: throw IllegalStateException("No se pudo medir la foto tomada.")
}

private fun calculateDecodeSample(width: Int, height: Int, maxSidePx: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxSidePx) sample *= 2
    return sample
}

private fun orientationSwapsDimensions(orientation: Int): Boolean = orientation in setOf(
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270
)

internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun PreparedFoodImage.toGeminiImageRequest(): GeminiImageRequest = GeminiImageRequest(
    imageBytes = bytes,
    mimeType = mimeType,
    originalBytes = originalBytes,
    originalWidth = originalWidth,
    originalHeight = originalHeight,
    preparedWidth = width,
    preparedHeight = height,
    quality = quality
)

private fun Float.cleanConfidence(): String = "%.0f%%".format(Locale.US, this.coerceIn(0f, 1f) * 100f)

private fun Float.cleanMacro(): String {
    return if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.US, this)
}

private fun ParsedNutritionLabel.hasAnyNutritionValue(): Boolean {
    return listOf(
        calories,
        proteinGrams,
        carbsGrams,
        fatGrams,
        saturatedFatGrams,
        sugarGrams,
        fiberGrams,
        sodiumMg
    ).any { it != null } || !servingSizeLabel.isNullOrBlank()
}

private fun Double?.toInputString(): String {
    val value = this ?: return ""
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)
}

private fun String.toDoubleInput(): Double? {
    return trim().replace(",", ".").toDoubleOrNull()
}

private fun String.toFloatInput(): Float? {
    return toDoubleInput()?.toFloat()
}

private fun nutritionStartOfTodayMillis(): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

