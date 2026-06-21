package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.BuildConfig
import com.example.data.model.UserDiscipline
import com.example.ui.theme.GoldenAmber
import com.example.ui.muscles.MuscleMapScreen
import com.example.ui.viewmodel.FitPlanViewModel

// ==========================================
// CENTRAL NAVIGATION DESTINATIONS
// ==========================================
object Destinations {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val NUTRITION = "nutrition"
    const val WORKOUT = "workout"
    const val HEALTH = "health"
    const val PROGRESS = "progress"
    const val MUSCLE_MAP = "muscle_map"
    const val PREMIUM = "premium"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val PERFORMANCE_SETTINGS = "performance_settings"
    const val DEVELOPER_SETTINGS = "developer_settings"
    const val DAILY_CHECK_IN = "daily_check_in"
    const val DAILY_MISSIONS = "daily_missions"
    const val ACHIEVEMENTS = "achievements"
    const val ACTIVE_RUN = "active_run"
    const val ACTIVE_RIDE = "active_ride"
    const val ACTIVE_WALK = "active_walk"
    const val WELLNESS_DIARY = "wellness_diary"
    const val DAILY_PLAN = "daily_plan"
    const val RUNNING_HISTORY = "running_history"
    const val CYCLING_HISTORY = "cycling_history"
    const val WALKING_HISTORY = "walking_history"
    const val ACTIVITY_SESSION_DETAIL = "activity_session_detail"
}

@Composable
fun FitPlanAppContent(
    viewModel: FitPlanViewModel,
    navController: NavHostController
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    val featureSettings by viewModel.featureSettings.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = backStackEntry?.destination?.route ?: Destinations.LOGIN

    fun navigate(destination: String) {
        if (destination == currentScreen) return
        navController.navigate(destination) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun replaceStack(destination: String) {
        if (destination == currentScreen) return
        navController.navigate(destination) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    // Enforce automatic redirection based on auth/onboarding states
    LaunchedEffect(isLoggedIn, isOnboardingCompleted, currentScreen) {
        if (!isLoggedIn && currentScreen != Destinations.LOGIN) {
            replaceStack(Destinations.LOGIN)
        } else if (isLoggedIn && !isOnboardingCompleted && currentScreen != Destinations.ONBOARDING) {
            replaceStack(Destinations.ONBOARDING)
        } else if (isLoggedIn && isOnboardingCompleted && (currentScreen == Destinations.LOGIN || currentScreen == Destinations.ONBOARDING)) {
            replaceStack(Destinations.DASHBOARD)
        }
    }

    LaunchedEffect(currentScreen, featureSettings) {
        val hiddenRouteRequested =
                (currentScreen == Destinations.NUTRITION && !featureSettings.nutritionEnabled) ||
                (currentScreen == Destinations.WORKOUT && !featureSettings.workoutEnabled) ||
                (currentScreen == Destinations.HEALTH && !featureSettings.healthEnabled) ||
                (currentScreen == Destinations.PROGRESS && !featureSettings.progressEnabled) ||
                (currentScreen == Destinations.DEVELOPER_SETTINGS && !BuildConfig.DEBUG)

        if (isLoggedIn && isOnboardingCompleted && hiddenRouteRequested) {
            replaceStack(Destinations.DASHBOARD)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.LOGIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Destinations.LOGIN) { LoginScreen(viewModel) }
        composable(Destinations.ONBOARDING) { OnboardingScreen(viewModel, ::navigate) }
        composable(Destinations.DASHBOARD) { DashboardScreen(viewModel, ::navigate) }
        composable(Destinations.NUTRITION) { NutritionScreen(viewModel, ::navigate) }
        composable(Destinations.WORKOUT) { WorkoutScreen(viewModel, ::navigate) }
        composable(Destinations.HEALTH) { HealthScreen(viewModel, ::navigate) }
        composable(Destinations.PROGRESS) { ProgressScreen(viewModel, ::navigate) }
        composable(Destinations.MUSCLE_MAP) {
            val isPremium by viewModel.isPremiumActive.collectAsStateWithLifecycle()
            val user by viewModel.userProfile.collectAsStateWithLifecycle()
            val discipline = remember(user?.trainingDiscipline) {
                UserDiscipline.fromStoredName(user?.trainingDiscipline)
            }
            MuscleMapScreen(::navigate, isPremium, discipline)
        }
        composable(Destinations.PREMIUM) { PremiumScreen(viewModel, ::navigate) }
        composable(Destinations.PROFILE) { ProfileScreen(viewModel, ::navigate) }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigate = ::navigate,
                onBack = {
                    if (!navController.popBackStack()) {
                        navigate(Destinations.DASHBOARD)
                    }
                }
            )
        }
        composable(Destinations.PERFORMANCE_SETTINGS) { PerformanceSettingsScreen(viewModel, ::navigate) }
        composable(Destinations.DEVELOPER_SETTINGS) {
            if (BuildConfig.DEBUG) DeveloperSettingsScreen(viewModel, ::navigate) else DashboardScreen(viewModel, ::navigate)
        }
        composable(Destinations.DAILY_CHECK_IN) { DailyCheckInScreen(::navigate, viewModel) }
        composable(Destinations.DAILY_MISSIONS) { DailyMissionsScreen(::navigate) }
        composable(Destinations.ACHIEVEMENTS) { AchievementsScreen(::navigate) }
        composable(Destinations.ACTIVE_RUN) { RunLogScreen(viewModel, ::navigate) }
        composable(Destinations.ACTIVE_RIDE) { RideLogScreen(viewModel, ::navigate) }
        composable(Destinations.ACTIVE_WALK) { WalkLogScreen(viewModel, ::navigate) }
        composable(Destinations.WELLNESS_DIARY) { WellnessDiaryScreen(viewModel, ::navigate) }
        composable(Destinations.DAILY_PLAN) { DailyPlanScreen(viewModel, ::navigate) }
        composable(Destinations.RUNNING_HISTORY) { RunningHistoryScreen(viewModel, ::navigate) }
        composable(Destinations.CYCLING_HISTORY) { CyclingHistoryScreen(viewModel, ::navigate) }
        composable(Destinations.WALKING_HISTORY) { WalkHistoryScreen(viewModel, ::navigate) }
        composable("${Destinations.ACTIVITY_SESSION_DETAIL}/{sessionId}") { entry ->
            ActivitySessionDetailScreen(
                viewModel = viewModel,
                sessionId = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L,
                onNavigate = ::navigate
            )
        }
    }
}

// Custom typography helper to avoid non-existent property compiles
@Composable
fun androidx.compose.material3.Typography.bodySmallNormal() = this.bodySmall.copy(fontWeight = FontWeight.Bold)

@Composable
fun androidx.compose.material3.Typography.labelNormal() = this.labelLarge.copy(fontWeight = FontWeight.Bold)

// ==========================================
// CENTRAL NAVIGATION BOTTOM BAR
// ==========================================
@Composable
fun FitPlanBottomBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    isPremium: Boolean,
    discipline: UserDiscipline = UserDiscipline.GYM
) {
    val isDark = LocalThemeIsDark.current
    val items = remember(discipline) { DisciplineNavigationPolicy.bottomItemsFor(discipline) }
    NavigationBar(
        containerColor = if (isDark) Color(0xD90F172A) else Color(0xD9FDFCFF),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isDark) Color(0x33FFFFFF) else Color(0x1F000000),
                        Color.Transparent
                    )
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
            )
    ) {
        items.forEach { item ->
            val selected = item.selectedRoutes.contains(selectedRoute)
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(item.route) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (item.route == Destinations.PROFILE && isPremium) GoldenAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}
