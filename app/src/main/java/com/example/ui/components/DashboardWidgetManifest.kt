package com.example.ui.components

import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.VisualPerformanceProfile

enum class DashboardWidgetId {
    TODAY_PLAN,
    GAMIFICATION,
    WORKOUT,
    NUTRITION,
    HEALTH,
    PROGRESS,
    AI_COACH,
    PREMIUM
}

enum class WidgetState {
    ACTIVE,
    LOCKED,
    CONNECT_REQUIRED,
    EMPTY_STATE,
    HIDDEN,
    SYSTEM_PINNED,
    DISABLED
}

data class DashboardWidgetManifestItem(
    val id: DashboardWidgetId,
    val state: WidgetState,
    val title: String,
    val message: String,
    val systemPinned: Boolean = false
) {
    val shouldRender: Boolean
        get() = state != WidgetState.HIDDEN
}

object DashboardWidgetManifestResolver {
    fun resolve(
        user: UserEntity?,
        isPremium: Boolean,
        todayMeals: List<FoodLogEntity>,
        todayHealth: List<HealthLogEntity>,
        recentHealth: List<HealthLogEntity>,
        profile: VisualPerformanceProfile
    ): List<DashboardWidgetManifestItem> {
        val hasOnboardingProfile = user != null
        val hasNutritionData = todayMeals.isNotEmpty()
        val hasHealthData = todayHealth.isNotEmpty()
        val hasProgressData = recentHealth.any { it.weightKg > 0f }

        val items = listOf(
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.TODAY_PLAN,
                state = WidgetState.SYSTEM_PINNED,
                title = "Plan de hoy",
                message = "Acciones principales para avanzar hoy.",
                systemPinned = true
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.GAMIFICATION,
                state = WidgetState.SYSTEM_PINNED,
                title = "Racha y misiones",
                message = "Hábitos reales, no solo abrir la app.",
                systemPinned = true
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.WORKOUT,
                state = if (hasOnboardingProfile) WidgetState.ACTIVE else WidgetState.EMPTY_STATE,
                title = "Entrenamiento de hoy",
                message = if (hasOnboardingProfile) "Rutina sugerida lista." else "Completa tu onboarding para crear tu plan.",
                systemPinned = true
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.NUTRITION,
                state = if (hasNutritionData) WidgetState.ACTIVE else WidgetState.EMPTY_STATE,
                title = "Nutrición",
                message = if (hasNutritionData) "Resumen de comidas de hoy." else "Registra tu primera comida.",
                systemPinned = true
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.HEALTH,
                state = if (hasHealthData) WidgetState.ACTIVE else WidgetState.EMPTY_STATE,
                title = "Agua y recuperación",
                message = if (hasHealthData) "Datos de salud de hoy." else "Agrega agua, sueño o energía para ver recomendaciones."
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.PROGRESS,
                state = if (hasProgressData) WidgetState.ACTIVE else WidgetState.EMPTY_STATE,
                title = "Progreso",
                message = if (hasProgressData) "Tendencia disponible." else "Registra peso o medidas para ver avances."
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.AI_COACH,
                state = if (isPremium) WidgetState.ACTIVE else WidgetState.LOCKED,
                title = "FitCore AI",
                message = if (isPremium) "Coach listo para ayudarte." else "Coach IA disponible en Premium."
            ),
            DashboardWidgetManifestItem(
                id = DashboardWidgetId.PREMIUM,
                state = if (isPremium) WidgetState.HIDDEN else WidgetState.LOCKED,
                title = "Premium",
                message = "Desbloquea coach IA, reportes y ajustes automáticos."
            )
        )

        val safeLimit = profile.maxDashboardWidgets.coerceAtLeast(5)
        val pinned = items.filter { it.systemPinned || it.state == WidgetState.SYSTEM_PINNED }
        val optional = items.filterNot { it.systemPinned || it.state == WidgetState.SYSTEM_PINNED }
            .filter { it.shouldRender }
            .take((safeLimit - pinned.size).coerceAtLeast(0))
        return pinned + optional
    }

    fun stateOf(
        manifest: List<DashboardWidgetManifestItem>,
        id: DashboardWidgetId
    ): WidgetState {
        return manifest.firstOrNull { it.id == id }?.state ?: WidgetState.HIDDEN
    }
}

fun WidgetState.shouldRenderWidget(): Boolean {
    return this != WidgetState.HIDDEN && this != WidgetState.DISABLED
}

