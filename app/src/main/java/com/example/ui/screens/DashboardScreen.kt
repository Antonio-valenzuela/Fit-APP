package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.analytics.ProductAnalytics
import com.example.data.model.ActivitySessionSummaryRules
import com.example.data.model.LocalRecommendationRules
import com.example.data.model.LocalRecommendation
import com.example.data.model.UserDiscipline
import com.example.data.model.VisualPerformanceProfile
import com.example.ui.components.AdaptiveDashboardRules
import com.example.ui.components.AdaptiveProgressWidget
import com.example.ui.components.DashboardWidgetId
import com.example.ui.components.DashboardWidgetManifestResolver
import com.example.ui.components.DashboardWidgetContainer
import com.example.ui.components.WidgetState
import com.example.ui.components.shouldRenderWidget
import com.example.ui.theme.AlertBlue
import com.example.ui.theme.AlertRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.frostedMeshBackground
import com.example.ui.muscles.MuscleMapViewModel
import com.example.data.model.Muscle
import com.example.ui.viewmodel.FitPlanViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.StreakViewModel
import com.example.ui.viewmodel.DailyMissionViewModel
import com.example.ui.viewmodel.AchievementViewModel
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: FitPlanViewModel, onNavigate: (String) -> Unit) {
    val settings by viewModel.featureSettings.collectAsStateWithLifecycle()
    val detectedProfile by viewModel.visualPerformanceProfile.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
    val premiumAvailable = true
    val effectiveIsPremium = premiumAvailable && isPremium
    val user by viewModel.userProfile.collectAsStateWithLifecycle()
    val activitySessions by viewModel.allActivitySessions.collectAsStateWithLifecycle()
    val workouts by viewModel.allWorkoutLogs.collectAsStateWithLifecycle()
    val discipline = remember(user?.trainingDiscipline) {
        UserDiscipline.fromStoredName(user?.trainingDiscipline)
    }
    val disciplineSessions = remember(activitySessions, discipline) {
        activitySessions.filter { it.discipline == discipline.name }
    }
    val disciplineSummary = remember(disciplineSessions) {
        ActivitySessionSummaryRules.summarize(disciplineSessions)
    }
    val muscleMapViewModel: MuscleMapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val muscleState by muscleMapViewModel.state.collectAsStateWithLifecycle()
    val isDark = LocalThemeIsDark.current
    val screenInfo = LocalScreenInfo.current
    val profile = remember(detectedProfile, settings.animationsEnabled, settings.chartsEnabled) {
        detectedProfile.copy(
            animationsEnabled = detectedProfile.animationsEnabled && settings.animationsEnabled,
            advancedAnimationsEnabled = detectedProfile.advancedAnimationsEnabled && settings.animationsEnabled,
            chartsEnabled = detectedProfile.chartsEnabled && settings.chartsEnabled,
            advancedChartsEnabled = detectedProfile.advancedChartsEnabled && settings.chartsEnabled
        )
    }
    val performanceMode = profile.useLightweightCards
    val dashboardSettings = remember(settings, effectiveIsPremium, premiumAvailable) {
        settings.copy(
            aiRecommendationsEnabled = effectiveIsPremium,
            premiumEnabled = premiumAvailable && !effectiveIsPremium
        )
    }
    val recentHealthLogsFlow = remember(profile.maxHistoryItems) {
        viewModel.getRecentHealthLogs(profile.maxHistoryItems)
    }

    // Only collect and observe data if its modular features are enabled
    val todayMeals = if (AdaptiveDashboardRules.shouldLoadNutrition(dashboardSettings)) {
        viewModel.todayFoodLogs.collectAsStateWithLifecycle().value
    } else emptyList()

    val todayHealth = if (AdaptiveDashboardRules.shouldLoadTodayHealth(dashboardSettings)) {
        viewModel.todayHealthLogs.collectAsStateWithLifecycle().value
    } else emptyList()

    val allHealthLogs = if (AdaptiveDashboardRules.shouldLoadProgressHistory(dashboardSettings)) {
        recentHealthLogsFlow.collectAsStateWithLifecycle(initialValue = emptyList()).value
    } else emptyList()
    val localRecommendations = remember(discipline, user?.goal, workouts, activitySessions, todayHealth, todayMeals, muscleState.recommendation?.muscle) {
        LocalRecommendationRules.build(
            discipline = discipline,
            goal = user?.goal.orEmpty(),
            workouts = workouts,
            activitySessions = activitySessions,
            todayHealthLogs = todayHealth,
            todayFoodLogs = todayMeals,
            muscleSuggestion = muscleState.recommendation?.muscle
        )
    }

    // Aggregations on demand (Safe & Lightweight compute)
    val todayWater = todayHealth.sumOf { it.waterIntakeMl }
    val lastGlucoseLog = todayHealth.firstOrNull { it.glucoseLevel > 0 }
    val lastBPLog = todayHealth.firstOrNull { it.systolicPressure > 0 }
    val widgetManifest = remember(user, effectiveIsPremium, todayMeals, todayHealth, allHealthLogs, profile) {
        DashboardWidgetManifestResolver.resolve(
            user = user,
            isPremium = effectiveIsPremium,
            todayMeals = todayMeals,
            todayHealth = todayHealth,
            recentHealth = allHealthLogs,
            profile = profile
        )
    }

    fun widgetState(id: DashboardWidgetId): WidgetState {
        return DashboardWidgetManifestResolver.stateOf(widgetManifest, id)
    }

    fun shouldRenderWidget(id: DashboardWidgetId): Boolean {
        return widgetState(id).shouldRenderWidget()
    }

    LaunchedEffect(widgetManifest) {
        ProductAnalytics.track(
            "home_rendered",
            mapOf(
                "widgets" to widgetManifest.count { it.shouldRender }.toString(),
                "locked" to widgetManifest.count { it.state == WidgetState.LOCKED }.toString()
            )
        )
        widgetManifest.filter { it.shouldRender }.forEach {
            ProductAnalytics.track("widget_seen", mapOf("widget_id" to it.id.name, "state" to it.state.name))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = if (profile.gradientsEnabled) {
            Modifier.frostedMeshBackground(isDark)
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        },
        bottomBar = { FitPlanBottomBar(Destinations.DASHBOARD, onNavigate, effectiveIsPremium, discipline) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Header Welcome Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hola, ${user?.name ?: "bienvenido"}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Meta: " + (user?.goal ?: "Progreso diario"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (premiumAvailable) {
                    IconButton(onClick = { onNavigate(Destinations.PREMIUM) }) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "FitPlan Premium",
                            tint = if (effectiveIsPremium) GoldenAmber else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            val hasGamificationWidgets = shouldRenderWidget(DashboardWidgetId.GAMIFICATION)
            val hasAnyMainWidget = widgetManifest.any { it.shouldRender }
            val dashboardWidgetLimit = AdaptiveDashboardRules.dashboardWidgetLimit(profile)
            var dashboardWidgetCount = 0
            fun canShowDashboardWidget(): Boolean {
                if (dashboardWidgetCount >= dashboardWidgetLimit) return false
                dashboardWidgetCount += 1
                return true
            }

            // Central Dynamic Grid Layout supporting compact vertical list or large double column tablet wide layout
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    DisciplineHeroCard(
                        discipline = discipline,
                        summary = disciplineSummary,
                        onPrimaryAction = { onNavigate(DisciplineNavigationPolicy.primaryActionRouteFor(discipline)) },
                        onChange = { onNavigate(Destinations.SETTINGS) }
                    )
                }

                item {
                    LocalRecommendationCard(
                        recommendation = localRecommendations.first(),
                        onAction = {
                            onNavigate(
                                when (localRecommendations.first().routeHint) {
                                    "active_run" -> Destinations.ACTIVE_RUN
                                    "active_ride" -> Destinations.ACTIVE_RIDE
                                    "wellness_diary" -> Destinations.WELLNESS_DIARY
                                    "daily_plan" -> Destinations.DAILY_PLAN
                                    "nutrition" -> Destinations.NUTRITION
                                    "health" -> Destinations.HEALTH
                                    else -> Destinations.WORKOUT
                                }
                            )
                        }
                    )
                }

                when (discipline) {
                    UserDiscipline.RUNNING, UserDiscipline.CYCLING, UserDiscipline.WALKING -> {
                        item {
                            CardioDisciplineDashboardCard(
                                discipline = discipline,
                                sessions = disciplineSessions,
                                onStart = { onNavigate(DisciplineNavigationPolicy.primaryActionRouteFor(discipline)) },
                                onHistory = {
                                    onNavigate(
                                        when (discipline) {
                                            UserDiscipline.RUNNING -> Destinations.RUNNING_HISTORY
                                            UserDiscipline.WALKING -> Destinations.WALKING_HISTORY
                                            else -> Destinations.CYCLING_HISTORY
                                        }
                                    )
                                }
                            )
                        }
                    }
                    UserDiscipline.WELLNESS -> {
                        item {
                            WellnessDisciplineDashboardCard(
                                todayWater = todayWater,
                                entryCount = todayHealth.size,
                                onDiary = { onNavigate(Destinations.WELLNESS_DIARY) },
                                onCheckIn = { onNavigate(Destinations.DAILY_CHECK_IN) }
                            )
                        }
                    }
                    UserDiscipline.HYBRID -> {
                        item {
                            HybridDisciplineDashboardCard(
                                onPlan = { onNavigate(Destinations.DAILY_PLAN) },
                                onRun = { onNavigate(Destinations.ACTIVE_RUN) },
                                onRide = { onNavigate(Destinations.ACTIVE_RIDE) },
                                onWellness = { onNavigate(Destinations.WELLNESS_DIARY) }
                            )
                        }
                    }
                    UserDiscipline.GYM -> Unit
                }

                if (discipline == UserDiscipline.GYM || discipline == UserDiscipline.HYBRID) {
                    item {
                        TodayActionStrip(
                            settings = dashboardSettings,
                            profile = profile,
                            onNavigate = onNavigate
                        )
                    }

                    item {
                        DashboardMuscleBalanceCard(
                            recommendedMuscle = muscleState.recommendation?.muscle,
                            reason = muscleState.recommendation?.reason,
                            hasData = muscleState.hasWorkoutData,
                            onBalance = { onNavigate(Destinations.MUSCLE_MAP) },
                            onTrain = { onNavigate(Destinations.WORKOUT) }
                        )
                    }
                }
                
                if (hasGamificationWidgets && canShowDashboardWidget()) item {
                    GamificationProgressWidgets(
                        settings = dashboardSettings,
                        profile = profile,
                        onNavigate = onNavigate
                    )
                }

                if (!hasAnyMainWidget) {
                    item {
                        DashboardEmptyState(onNavigate = onNavigate)
                    }
                }

                // MULTI-COLUMN TABLET HANDLING
                if (screenInfo.isTabletOrWider) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (shouldRenderWidget(DashboardWidgetId.NUTRITION) && canShowDashboardWidget()) {
                                    NutritionSummaryWidget(
                                        totalMeals = todayMeals,
                                        targetCals = user?.targetCalories ?: 2000,
                                        targetP = user?.targetProteinG ?: 140,
                                        targetC = user?.targetCarbsG ?: 220,
                                        targetF = user?.targetFatG ?: 65,
                                        performanceMode = performanceMode,
                                        profile = profile,
                                        onNavigate = onNavigate
                                    )
                                }

                                if (shouldRenderWidget(DashboardWidgetId.HEALTH) && canShowDashboardWidget()) {
                                    HealthSummaryWidget(
                                        todayWater = todayWater,
                                        lastBPLog = lastBPLog?.let { "${it.systolicPressure}/${it.diastolicPressure}" },
                                        lastGlucoseLog = lastGlucoseLog?.let { "${it.glucoseLevel} mg/dL" },
                                        performanceMode = performanceMode,
                                        profile = profile,
                                        onNavigate = onNavigate
                                    )
                                }

                                if (premiumAvailable && shouldRenderWidget(DashboardWidgetId.AI_COACH) && canShowDashboardWidget()) {
                                    AIRecommendationWidget(
                                        isPremium = effectiveIsPremium,
                                        performanceMode = performanceMode,
                                        profile = profile,
                                        onNavigate = onNavigate,
                                        onDisableAI = { onNavigate(Destinations.PROFILE) }
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (shouldRenderWidget(DashboardWidgetId.WORKOUT) && canShowDashboardWidget()) {
                                    WorkoutTodayWidget(
                                        performanceMode = performanceMode,
                                        profile = profile,
                                        onNavigate = onNavigate,
                                        onStartWorkout = { onNavigate(Destinations.WORKOUT) }
                                    )
                                }

                                if (shouldRenderWidget(DashboardWidgetId.PROGRESS) && canShowDashboardWidget()) {
                                    ProgressSummaryWidget(
                                        allHealthLogs = allHealthLogs,
                                        chartsEnabled = AdaptiveDashboardRules.shouldRenderCharts(profile, dashboardSettings),
                                        performanceMode = performanceMode,
                                        profile = profile,
                                        healthEnabled = true,
                                        onNavigate = onNavigate
                                    )
                                }

                                if (premiumAvailable && shouldRenderWidget(DashboardWidgetId.PREMIUM) && !effectiveIsPremium && canShowDashboardWidget()) {
                                    PremiumBenefitsWidget(
                                        profile = profile,
                                        onNavigate = onNavigate
                                    )
                                }

                                SettingsShortcutCard(onNavigate = onNavigate)
                            }
                        }
                    }
                } else {
                    // COMPACT PORTRAIT LAYOUT
                    if (shouldRenderWidget(DashboardWidgetId.NUTRITION) && canShowDashboardWidget()) {
                        item {
                            NutritionSummaryWidget(
                                totalMeals = todayMeals,
                                targetCals = user?.targetCalories ?: 2000,
                                targetP = user?.targetProteinG ?: 140,
                                targetC = user?.targetCarbsG ?: 220,
                                targetF = user?.targetFatG ?: 65,
                                performanceMode = performanceMode,
                                profile = profile,
                                onNavigate = onNavigate
                            )
                        }
                    }

                    if (shouldRenderWidget(DashboardWidgetId.WORKOUT) && canShowDashboardWidget()) {
                        item {
                            WorkoutTodayWidget(
                                performanceMode = performanceMode,
                                profile = profile,
                                onNavigate = onNavigate,
                                onStartWorkout = { onNavigate(Destinations.WORKOUT) }
                            )
                        }
                    }

                    if (shouldRenderWidget(DashboardWidgetId.HEALTH) && canShowDashboardWidget()) {
                        item {
                            HealthSummaryWidget(
                                todayWater = todayWater,
                                lastBPLog = lastBPLog?.let { "${it.systolicPressure}/${it.diastolicPressure}" },
                                lastGlucoseLog = lastGlucoseLog?.let { "${it.glucoseLevel} mg/dL" },
                                performanceMode = performanceMode,
                                profile = profile,
                                onNavigate = onNavigate
                            )
                        }
                    }

                    if (shouldRenderWidget(DashboardWidgetId.PROGRESS) && canShowDashboardWidget()) {
                        item {
                            ProgressSummaryWidget(
                                allHealthLogs = allHealthLogs,
                                chartsEnabled = AdaptiveDashboardRules.shouldRenderCharts(profile, dashboardSettings),
                                performanceMode = performanceMode,
                                profile = profile,
                                healthEnabled = true,
                                onNavigate = onNavigate
                            )
                        }
                    }

                    if (premiumAvailable && shouldRenderWidget(DashboardWidgetId.AI_COACH) && canShowDashboardWidget()) {
                        item {
                            AIRecommendationWidget(
                                isPremium = effectiveIsPremium,
                                performanceMode = performanceMode,
                                profile = profile,
                                onNavigate = onNavigate,
                                onDisableAI = { onNavigate(Destinations.PROFILE) }
                            )
                        }
                    }

                    if (premiumAvailable && shouldRenderWidget(DashboardWidgetId.PREMIUM) && !effectiveIsPremium && canShowDashboardWidget()) {
                        item {
                            PremiumBenefitsWidget(
                                profile = profile,
                                onNavigate = onNavigate
                            )
                        }
                    }

                    item {
                        SettingsShortcutCard(onNavigate = onNavigate)
                    }
                }
            }
        }
    }
}

// ==========================================
// INDEPENDENT STANDALONE WIDGET COMPONENTS
// ==========================================

@Composable
private fun DashboardMuscleBalanceCard(
    recommendedMuscle: Muscle?,
    reason: String?,
    hasData: Boolean,
    onBalance: () -> Unit,
    onTrain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (hasData) "Balance muscular" else "Empieza tu balance muscular",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = recommendedMuscle?.let { "Hoy conviene trabajar ${it.displayName}." }
                    ?: "Completa un entrenamiento para ver que musculos trabajaste.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            reason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBalance, modifier = Modifier.weight(1f)) {
                    Text("Ver balance")
                }
                Button(onClick = onTrain, modifier = Modifier.weight(1f)) {
                    Text("Ver rutina")
                }
            }
        }
    }
}

@Composable
private fun DisciplineHeroCard(
    discipline: UserDiscipline,
    summary: com.example.data.model.ActivitySessionSummary,
    onPrimaryAction: () -> Unit,
    onChange: () -> Unit
) {
    val accentColor = remember(discipline) {
        runCatching {
            Color(android.graphics.Color.parseColor(discipline.accentColorHex).toLong() or 0xFF000000)
        }.getOrDefault(Color(0xFF3B5BDB))
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = discipline.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = discipline.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onChange) {
                    Text("Cambiar")
                }
            }

            when (discipline) {
                UserDiscipline.RUNNING, UserDiscipline.CYCLING, UserDiscipline.WALKING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DisciplineStatPill(
                            label = "Distancia",
                            value = summary.distanceText ?: "Sin datos",
                            modifier = Modifier.weight(1f)
                        )
                        DisciplineStatPill(
                            label = if (discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) "Ritmo" else "Velocidad",
                            value = if (discipline == UserDiscipline.RUNNING || discipline == UserDiscipline.WALKING) {
                                summary.paceText ?: "Sin datos"
                            } else {
                                summary.speedText ?: "Sin datos"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                UserDiscipline.WELLNESS -> {
                    Text(
                        text = "Usa tu diario para registrar agua, sueno, energia y peso sin inventar metricas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UserDiscipline.HYBRID -> {
                    Text(
                        text = "Combina una sesión de fuerza, una acción cardio y un check-in de bienestar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UserDiscipline.GYM -> {
                    Text(
                        text = "Tu experiencia principal sigue siendo rutina, cargas y balance muscular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onPrimaryAction,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(discipline.actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DisciplineStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CardioDisciplineDashboardCard(
    discipline: UserDiscipline,
    sessions: List<com.example.data.model.ActivitySessionEntity>,
    onStart: () -> Unit,
    onHistory: () -> Unit
) {
    val weekStart = remember { dashboardStartOfWeekMillis() }
    val weekSessions = remember(sessions, weekStart) {
        sessions.filter { (it.startedAt.takeIf { value -> value > 0L } ?: it.timestamp) >= weekStart }
    }
    val weekSummary = remember(weekSessions) { ActivitySessionSummaryRules.summarize(weekSessions) }
    val lastSession = sessions.maxByOrNull { it.endedAt.takeIf { value -> value > 0L } ?: it.timestamp }
    val isRun = discipline == UserDiscipline.RUNNING
    val title = if (isRun) "Tu semana running" else "Tu semana en bici"
    val empty = if (isRun) "Sin carreras todavia" else "Sin rodadas todavia"
    val startText = if (isRun) "Iniciar carrera" else "Iniciar rodada"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DisciplineStatPill("Km semana", weekSummary.distanceText ?: "0.0 km", Modifier.weight(1f))
                DisciplineStatPill(
                    if (isRun) "Ritmo prom." else "Vel. prom.",
                    if (isRun) weekSummary.paceText ?: "Sin datos" else weekSummary.speedText ?: "Sin datos",
                    Modifier.weight(1f)
                )
            }
            Text(
                lastSession?.let {
                    val duration = it.durationSeconds.takeIf { value -> value > 0L } ?: it.durationMinutes * 60L
                    String.format(Locale.US, "Ultima: %.2f km / %s", it.distanceKm, dashboardFormatDuration(duration))
                } ?: empty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text(startText) }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Historial") }
            }
        }
    }
}

@Composable
private fun WellnessDisciplineDashboardCard(
    todayWater: Int,
    entryCount: Int,
    onDiary: () -> Unit,
    onCheckIn: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bienestar de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DisciplineStatPill("Agua", if (todayWater > 0) "${todayWater} ml" else "Sin datos", Modifier.weight(1f))
                DisciplineStatPill("Entradas", entryCount.toString(), Modifier.weight(1f))
            }
            Text(
                if (entryCount == 0) "Registra agua, sueno, energia o peso para construir tu resumen." else "Tu diario ya tiene registros de hoy.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDiary, modifier = Modifier.weight(1f)) { Text("Diario") }
                OutlinedButton(onClick = onCheckIn, modifier = Modifier.weight(1f)) { Text("Check-in") }
            }
        }
    }
}

@Composable
private fun HybridDisciplineDashboardCard(
    onPlan: () -> Unit,
    onRun: () -> Unit,
    onRide: () -> Unit,
    onWellness: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Plan mixto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Combina fuerza, cardio y bienestar en una guía diaria.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) { Text("Ver plan del día") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRun,
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Iniciar carrera", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onRide,
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Iniciar rodada", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onWellness,
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Abrir diario", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun TodayActionStrip(
    settings: com.example.data.model.FeatureSettings,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(if (profile.useLightweightCards) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (profile.shadowsEnabled) 3.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(if (profile.useLightweightCards) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Plan de hoy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Empieza con una acción pequeña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardQuickAction(
                    label = "Check-in",
                    icon = Icons.Default.Whatshot,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            if (settings.streakEnabled) Destinations.DAILY_CHECK_IN else Destinations.SETTINGS
                        )
                    }
                )
                DashboardQuickAction(
                    label = "Comida",
                    icon = Icons.Default.RestaurantMenu,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            if (settings.nutritionEnabled) Destinations.NUTRITION else Destinations.SETTINGS
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardQuickAction(
                    label = "Rutina",
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            if (settings.workoutEnabled) Destinations.WORKOUT else Destinations.SETTINGS
                        )
                    }
                )
                DashboardQuickAction(
                    label = "Agua",
                    icon = Icons.Default.LocalDrink,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(
                            if (settings.healthEnabled) Destinations.HEALTH else Destinations.SETTINGS
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DashboardQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NutritionSummaryWidget(
    totalMeals: List<com.example.data.model.FoodLogEntity>,
    targetCals: Int,
    targetP: Int,
    targetC: Int,
    targetF: Int,
    performanceMode: Boolean,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit
) {
    val totalCals = totalMeals.sumOf { it.calories }
    val totalProtein = totalMeals.sumOf { it.proteinG.toDouble() }.toFloat()
    val totalCarbs = totalMeals.sumOf { it.carbsG.toDouble() }.toFloat()
    val totalFat = totalMeals.sumOf { it.fatG.toDouble() }.toFloat()
    DashboardWidgetContainer(
        title = "Nutrición",
        icon = Icons.Default.RestaurantMenu,
        performanceModeEnabled = performanceMode,
        visualProfile = profile,
        actionText = "Registrar comida",
        onActionClick = { onNavigate(Destinations.NUTRITION) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { if (targetCals > 0) (totalCals.toFloat() / targetCals).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val remCals = (targetCals - totalCals).coerceAtLeast(0)
                    Text(
                        "$remCals",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "restantes",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroDetailsRow("Prot", totalProtein.toInt(), targetP, "g", MaterialTheme.colorScheme.secondary)
                MacroDetailsRow("Carb", totalCarbs.toInt(), targetC, "g", AlertBlue)
                MacroDetailsRow("Grasa", totalFat.toInt(), targetF, "g", GoldenAmber)
            }
        }

        if (totalMeals.isEmpty()) {
            Text(
                text = "Todavía no tienes comidas registradas hoy. Agrega tu primera comida para ver tu resumen diario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { onNavigate(Destinations.NUTRITION) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            Text("Registrar comida", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
fun WorkoutTodayWidget(
    performanceMode: Boolean,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit,
    onStartWorkout: () -> Unit
) {
    DashboardWidgetContainer(
        title = "Entrenamiento",
        icon = Icons.Default.FitnessCenter,
        performanceModeEnabled = performanceMode,
        visualProfile = profile,
        actionText = "Ver Rutinas",
        onActionClick = { onNavigate(Destinations.WORKOUT) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Rutina sugerida",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Abre tus rutinas y registra el entrenamiento que haras hoy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.weight(1.5f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                Text("Ver rutina", fontSize = 12.sp)
                }
            }

            OutlinedButton(
                onClick = { onNavigate(Destinations.WORKOUT) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Historial", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ProgressSummaryWidget(
    allHealthLogs: List<com.example.data.model.HealthLogEntity>,
    chartsEnabled: Boolean,
    performanceMode: Boolean,
    profile: VisualPerformanceProfile,
    healthEnabled: Boolean,
    onNavigate: (String) -> Unit
) {
    val weightLogs = allHealthLogs.filter { it.weightKg > 0f }
    val points = remember(weightLogs) {
        weightLogs.map { it.weightKg }
    }
    val labels = remember(weightLogs) {
        if (weightLogs.isEmpty()) {
            emptyList()
        } else {
            weightLogs.mapIndexed { idx, _ -> "R${idx + 1}" }
        }
    }

    DashboardWidgetContainer(
        title = "Progreso",
        icon = Icons.Default.MonitorWeight,
        performanceModeEnabled = performanceMode,
        visualProfile = profile,
        actionText = "Ver progreso",
        onActionClick = { onNavigate(Destinations.PROGRESS) }
    ) {
        if (weightLogs.isEmpty()) {
            Text(
                "Todavía no tienes registros de peso. Registra tu primer avance para ver tu progreso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AdaptiveProgressWidget(
                profile = profile.copy(
                    chartsEnabled = chartsEnabled,
                    advancedChartsEnabled = profile.advancedChartsEnabled && chartsEnabled
                ),
                points = points,
                labels = labels
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onNavigate(if (healthEnabled) Destinations.HEALTH else Destinations.SETTINGS) },
                modifier = Modifier.weight(1.2f)
            ) {
                Text(if (healthEnabled) "Registrar peso" else "Activar salud", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HealthSummaryWidget(
    todayWater: Int,
    lastBPLog: String?,
    lastGlucoseLog: String?,
    performanceMode: Boolean,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit
) {
    DashboardWidgetContainer(
        title = "Salud diaria",
        icon = Icons.Default.Favorite,
        performanceModeEnabled = performanceMode,
        visualProfile = profile,
        actionText = "Registrar agua",
        onActionClick = { onNavigate(Destinations.HEALTH) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Glucose
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Opacity, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                    Text("Glucosa", style = MaterialTheme.typography.labelSmall)
                    Text(lastGlucoseLog ?: "Sin reg.", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }

            // Blood pressure
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                    Text("Presión", style = MaterialTheme.typography.labelSmall)
                    Text(lastBPLog ?: "Sin reg.", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }

            // Water intake
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocalDrink, contentDescription = null, tint = AlertBlue, modifier = Modifier.size(16.dp))
                    Text("Agua", style = MaterialTheme.typography.labelSmall)
                    Text("$todayWater mL", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onNavigate(Destinations.HEALTH) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Registrar agua", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AIRecommendationWidget(
    isPremium: Boolean,
    performanceMode: Boolean,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit,
    onDisableAI: () -> Unit
) {
    DashboardWidgetContainer(
        title = "Consejos inteligentes",
        icon = Icons.Default.AutoAwesome,
        performanceModeEnabled = performanceMode,
        visualProfile = profile,
        actionText = "Ver coach",
        onActionClick = onDisableAI
    ) {
        if (!isPremium) {
            Text(
                "Recibe consejos utiles a partir de tus registros. Esta experiencia forma parte del plan Premium.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { onNavigate(Destinations.PREMIUM) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar consejos premium", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        } else {
            Text(
                "Tus consejos inteligentes están listos cuando quieras revisar tus hábitos y tendencias.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { onNavigate(Destinations.PROFILE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver consejos", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PremiumBenefitsWidget(
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit
) {
    Card(
        border = BorderStroke(1.dp, GoldenAmber),
        colors = CardDefaults.cardColors(containerColor = GoldenAmber.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (profile.useLightweightCards) 12.dp else 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (profile.shadowsEnabled) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Stars, contentDescription = null, tint = GoldenAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("FitPlan Premium", fontWeight = FontWeight.Bold, color = GoldenAmber, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Coach IA, reportes avanzados y ajustes automaticos estaran disponibles con un plan Premium.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onNavigate(Destinations.PREMIUM) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text("Ver Premium", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onNavigate(Destinations.PROFILE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver coach", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DashboardEmptyState(
    onNavigate: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tu inicio está listo para personalizarse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Activa racha, nutrición, entrenamiento, salud o progreso desde Ajustes para ver tus tarjetas aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onNavigate(Destinations.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Abrir ajustes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsShortcutCard(
    onNavigate: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(Destinations.SETTINGS) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Ajustes del inicio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Activa modulos y preferencias seguras.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun MacroDetailsRow(label: String, consumed: Int, target: Int, unit: String, color: Color) {
    val progress = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("$consumed/$target $unit", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun GamificationProgressWidgets(
    settings: com.example.data.model.FeatureSettings,
    profile: VisualPerformanceProfile,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val streakViewModel: StreakViewModel = viewModel()
    val missionsViewModel: DailyMissionViewModel = viewModel()
    val achievementViewModel: AchievementViewModel = viewModel()

    val uiState by streakViewModel.uiState.collectAsStateWithLifecycle()
    val missions by missionsViewModel.missions.collectAsStateWithLifecycle()
    val achievements by achievementViewModel.achievements.collectAsStateWithLifecycle()

    // Refresh whenever visible motivation widgets change.
    LaunchedEffect(
        settings.streakEnabled,
        settings.dailyMissionsEnabled,
        settings.achievementsEnabled,
        settings.motivationMessagesEnabled
    ) {
        streakViewModel.refreshStreak()
        missionsViewModel.loadMissions()
        achievementViewModel.loadAchievements()
    }

    // Key achievement unlock overlay representation
    if (uiState.unlockedAchievement != null) {
        AlertDialog(
            onDismissRequest = { streakViewModel.dismissUnlockedAchievement() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldenAmber
                    )
                    Text("Insignia desbloqueada", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = uiState.unlockedAchievement?.achievementKey?.replace("ach_", "")?.replace("_", " ")?.uppercase() ?: "",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Buen trabajo. Desbloqueaste una nueva insignia por tu constancia.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        streakViewModel.dismissUnlockedAchievement()
                        achievementViewModel.loadAchievements()
                    }
                ) {
                    Text("Genial", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        // 1. Motivation card (Empathetic support text)
        if (settings.motivationMessagesEnabled && uiState.motivationMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acompañamiento FitPlan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = uiState.motivationMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Streak Widget
        if (settings.streakEnabled && settings.showStreakWidget) {
            DashboardWidgetContainer(
                title = "Tu Racha de Bienestar",
                icon = Icons.Default.Whatshot,
                performanceModeEnabled = profile.useLightweightCards,
                visualProfile = profile,
                actionText = "Ir al Check-in",
                onActionClick = { onNavigate(Destinations.DAILY_CHECK_IN) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = "Racha activa",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${uiState.currentStreak}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (uiState.currentStreak == 1) "día activo" else "días activos",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                text = "Mejor marca: ${uiState.bestStreak} días",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { onNavigate(Destinations.DAILY_CHECK_IN) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Registrar", fontWeight = FontWeight.Bold)
                    }
                    if (uiState.currentStreak > 0) {
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Llevo ${uiState.currentStreak} días de constancia en FitPlan MX."
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Compartir racha")
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir racha"
                            )
                        }
                    }
                }
            }
        }

        // 3. Daily Missions Widget
        if (settings.dailyMissionsEnabled) {
            val finishedMissions = missions.count { it.completed }
            val total = missions.size
            val pct = if (total > 0) finishedMissions.toFloat() / total else 0f

            DashboardWidgetContainer(
                title = "Misiones de hoy",
                icon = Icons.AutoMirrored.Filled.Assignment,
                performanceModeEnabled = profile.useLightweightCards,
                visualProfile = profile,
                actionText = if (total > 0) "Ver misiones ($finishedMissions/$total)" else "Ver misiones",
                onActionClick = { onNavigate(Destinations.DAILY_MISSIONS) }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (missions.isEmpty()) {
                        Text(
                            text = "Todavía no hay misiones listas para hoy. Abre Misiones para prepararlas y empezar con una meta pequeña.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onNavigate(Destinations.DAILY_MISSIONS) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ver misiones", fontWeight = FontWeight.Bold)
                        }
                        return@Column
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progreso del día",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(pct * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    missions.take(if (profile.useLightweightCards) 1 else 2).forEach { mission ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = mission.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (mission.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                color = if (mission.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (mission.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (mission.completed) "Completa" else "Pendiente",
                                tint = if (mission.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Achievements Widget
        if (settings.achievementsEnabled) {
            DashboardWidgetContainer(
                title = "Insignias desbloqueadas",
                icon = Icons.Default.Star,
                performanceModeEnabled = profile.useLightweightCards,
                visualProfile = profile,
                actionText = "Ver Vitrina (${achievements.size})",
                onActionClick = { onNavigate(Destinations.ACHIEVEMENTS) }
            ) {
                if (achievements.isEmpty()) {
                    Text(
                        text = "Completa hábitos saludables para ganar insignias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        achievements.take(if (profile.useLightweightCards) 3 else 5).forEach { ach ->
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Estrella",
                                        tint = GoldenAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = ach.achievementKey.replace("ach_", "").replace("_", " ").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dashboardStartOfWeekMillis(): Long {
    return Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun LocalRecommendationCard(
    recommendation: LocalRecommendation,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Recomendacion local", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(recommendation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(recommendation.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(recommendation.actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun dashboardFormatDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val secs = safeSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}
