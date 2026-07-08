package com.example.data.model

data class DashboardTodaySummary(
    val planTitle: String,
    val planMessage: String,
    val primaryActionLabel: String,
    val primaryRouteHint: String,
    val readinessLabel: String,
    val readinessMessage: String,
    val waterStatus: String,
    val foodStatus: String,
    val lastTrainingStatus: String,
    val muscleStatus: String
)

object DashboardTodaySummaryRules {
    fun build(
        recommendation: LocalRecommendation,
        todayHealthLogs: List<HealthLogEntity>,
        todayFoodLogs: List<FoodLogEntity>,
        allHealthLogs: List<HealthLogEntity> = todayHealthLogs,
        workouts: List<WorkoutLogEntity>,
        activitySessions: List<ActivitySessionEntity>,
        recommendedMuscle: Muscle?,
        discipline: UserDiscipline,
        nowMillis: Long = System.currentTimeMillis()
    ): DashboardTodaySummary {
        val monitor = HealthActivityMonitorRules.build(
            todayHealthLogs = todayHealthLogs,
            todayFoodLogs = todayFoodLogs,
            allHealthLogs = allHealthLogs,
            workouts = workouts,
            activitySessions = activitySessions,
            recommendedMuscle = recommendedMuscle,
            discipline = discipline,
            nowMillis = nowMillis
        )
        val readiness = ReadinessScoreRules.calculate(monitor)

        return DashboardTodaySummary(
            planTitle = planTitle(recommendation, monitor, readiness),
            planMessage = readiness.message,
            primaryActionLabel = recommendation.actionLabel,
            primaryRouteHint = primaryRouteHint(recommendation, monitor, readiness),
            readinessLabel = readiness.label,
            readinessMessage = if (monitor.dataConfidence == DataConfidence.LOW) {
                readiness.message
            } else {
                readiness.reasons.take(2).joinToString(" ")
            },
            waterStatus = waterStatus(monitor),
            foodStatus = foodStatus(monitor.mealCount),
            lastTrainingStatus = trainingStatus(monitor.daysSinceLastTraining),
            muscleStatus = muscleStatus(monitor, discipline)
        )
    }

    private fun planTitle(
        recommendation: LocalRecommendation,
        monitor: HealthActivityMonitor,
        readiness: ReadinessScore
    ): String = when {
        readiness.level == "Recuperación" -> "Recuperación activa"
        monitor.hydrationSignal == HydrationSignal.LOW -> "Hidratarte y moverte suave"
        monitor.neglectedMuscles.isNotEmpty() && readiness.score >= 60 ->
            "Entrena ${monitor.neglectedMuscles.first().displayName}"
        else -> recommendation.title
    }

    private fun primaryRouteHint(
        recommendation: LocalRecommendation,
        monitor: HealthActivityMonitor,
        readiness: ReadinessScore
    ): String = when {
        monitor.dataConfidence == DataConfidence.LOW -> recommendation.routeHint
        monitor.hydrationSignal == HydrationSignal.LOW || monitor.hydrationSignal == HydrationSignal.MISSING -> "health"
        MissingRecord.FOOD in monitor.missingRecords && readiness.score >= 60 -> "nutrition"
        readiness.level == "Recuperación" -> "wellness_diary"
        else -> recommendation.routeHint
    }

    private fun waterStatus(monitor: HealthActivityMonitor): String = when (monitor.hydrationSignal) {
        HydrationSignal.MISSING -> "Agua sin registrar"
        HydrationSignal.LOW -> "Hidratacion baja"
        HydrationSignal.OK -> "Agua registrada: ${monitor.waterMl} ml"
    }

    private fun foodStatus(mealCount: Int): String = when (mealCount) {
        0 -> "Falta registrar comida"
        1 -> "1 comida registrada"
        else -> "$mealCount comidas registradas"
    }

    private fun trainingStatus(daysSinceLastTraining: Int?): String {
        if (daysSinceLastTraining == null) return "Sin entrenamiento reciente"
        return when (daysSinceLastTraining) {
            0 -> "Entrenaste hoy"
            1 -> "Entrenaste ayer"
            else -> "Ultimo entrenamiento hace $daysSinceLastTraining dias"
        }
    }

    private fun muscleStatus(monitor: HealthActivityMonitor, discipline: UserDiscipline): String {
        if (monitor.neglectedMuscles.isNotEmpty()) {
            return "Pendiente: ${monitor.neglectedMuscles.first().displayName}"
        }
        if (monitor.recentlyWorkedMuscles.isNotEmpty()) {
            return "Reciente: ${monitor.recentlyWorkedMuscles.take(2).joinToString { it.displayName }}"
        }
        return when (discipline) {
            UserDiscipline.RUNNING,
            UserDiscipline.CYCLING,
            UserDiscipline.WALKING -> "Cardio activo"
            UserDiscipline.WELLNESS -> "Bienestar pendiente"
            else -> "Balance muscular sin datos"
        }
    }
}
