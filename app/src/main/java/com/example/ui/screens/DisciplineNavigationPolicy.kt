package com.example.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.UserDiscipline

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val selectedRoutes: Set<String> = setOf(route)
)

enum class DashboardCardType {
    DISCIPLINE_HERO,
    TODAY_ACTIONS,
    MUSCLE_BALANCE,
    RUNNING_HISTORY,
    CYCLING_HISTORY,
    WALKING_HISTORY,
    WELLNESS,
    HYBRID_PLAN,
    NUTRITION,
    PROGRESS,
    SETTINGS
}

object DisciplineNavigationPolicy {
    fun bottomItemsFor(discipline: UserDiscipline): List<BottomNavItem> {
        return when (discipline) {
            UserDiscipline.GYM -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Entrenar", Destinations.WORKOUT, Icons.Default.FitnessCenter),
                BottomNavItem("Musculos", Destinations.MUSCLE_MAP, Icons.Default.AccessibilityNew),
                BottomNavItem("Comida", Destinations.NUTRITION, Icons.Default.Restaurant),
                BottomNavItem("Perfil", Destinations.PROFILE, Icons.Default.Person)
            )
            UserDiscipline.RUNNING -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Carrera", Destinations.ACTIVE_RUN, Icons.AutoMirrored.Filled.DirectionsRun),
                BottomNavItem("Historial", Destinations.RUNNING_HISTORY, Icons.Default.CalendarMonth),
                BottomNavItem("Progreso", Destinations.PROGRESS, Icons.AutoMirrored.Filled.ShowChart),
                BottomNavItem("Perfil", Destinations.PROFILE, Icons.Default.Person)
            )
            UserDiscipline.CYCLING -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Rodada", Destinations.ACTIVE_RIDE, Icons.AutoMirrored.Filled.DirectionsBike),
                BottomNavItem("Historial", Destinations.CYCLING_HISTORY, Icons.Default.CalendarMonth),
                BottomNavItem("Progreso", Destinations.PROGRESS, Icons.AutoMirrored.Filled.ShowChart),
                BottomNavItem("Perfil", Destinations.PROFILE, Icons.Default.Person)
            )
            UserDiscipline.WALKING -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Caminata", Destinations.ACTIVE_WALK, Icons.AutoMirrored.Filled.DirectionsWalk),
                BottomNavItem("Historial", Destinations.WALKING_HISTORY, Icons.Default.CalendarMonth),
                BottomNavItem("Progreso", Destinations.PROGRESS, Icons.AutoMirrored.Filled.ShowChart),
                BottomNavItem("Perfil", Destinations.PROFILE, Icons.Default.Person)
            )
            UserDiscipline.WELLNESS -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Diario", Destinations.WELLNESS_DIARY, Icons.Default.MonitorHeart, setOf(Destinations.WELLNESS_DIARY, Destinations.HEALTH)),
                BottomNavItem("Habitos", Destinations.DAILY_CHECK_IN, Icons.Default.SelfImprovement),
                BottomNavItem("Resumen", Destinations.PROGRESS, Icons.AutoMirrored.Filled.ShowChart),
                BottomNavItem("Perfil", Destinations.PROFILE, Icons.Default.Person)
            )
            UserDiscipline.HYBRID -> listOf(
                BottomNavItem("Inicio", Destinations.DASHBOARD, Icons.Default.Home),
                BottomNavItem("Plan", Destinations.DAILY_PLAN, Icons.Default.CalendarMonth),
                BottomNavItem("Fuerza", Destinations.WORKOUT, Icons.Default.FitnessCenter),
                BottomNavItem("Cardio", Destinations.ACTIVE_RUN, Icons.Default.AddCircle, setOf(Destinations.ACTIVE_RUN, Destinations.ACTIVE_RIDE)),
                BottomNavItem("Bienestar", Destinations.WELLNESS_DIARY, Icons.Default.MonitorHeart, setOf(Destinations.WELLNESS_DIARY, Destinations.HEALTH))
            )
        }
    }

    fun primaryActionRouteFor(discipline: UserDiscipline): String {
        return discipline.primaryRoute
    }

    fun dashboardCardsFor(discipline: UserDiscipline): List<DashboardCardType> {
        return when (discipline) {
            UserDiscipline.GYM -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.TODAY_ACTIONS,
                DashboardCardType.MUSCLE_BALANCE,
                DashboardCardType.PROGRESS,
                DashboardCardType.SETTINGS
            )
            UserDiscipline.RUNNING -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.RUNNING_HISTORY,
                DashboardCardType.PROGRESS,
                DashboardCardType.SETTINGS
            )
            UserDiscipline.CYCLING -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.CYCLING_HISTORY,
                DashboardCardType.PROGRESS,
                DashboardCardType.SETTINGS
            )
            UserDiscipline.WALKING -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.WALKING_HISTORY,
                DashboardCardType.PROGRESS,
                DashboardCardType.SETTINGS
            )
            UserDiscipline.WELLNESS -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.WELLNESS,
                DashboardCardType.PROGRESS,
                DashboardCardType.SETTINGS
            )
            UserDiscipline.HYBRID -> listOf(
                DashboardCardType.DISCIPLINE_HERO,
                DashboardCardType.HYBRID_PLAN,
                DashboardCardType.TODAY_ACTIONS,
                DashboardCardType.SETTINGS
            )
        }
    }
}
