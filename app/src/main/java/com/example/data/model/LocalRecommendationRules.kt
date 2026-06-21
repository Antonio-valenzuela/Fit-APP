package com.example.data.model

import java.util.Calendar

data class LocalRecommendation(
    val title: String,
    val reason: String,
    val actionLabel: String,
    val routeHint: String
)

object LocalRecommendationRules {
    fun build(
        discipline: UserDiscipline,
        goal: String,
        workouts: List<WorkoutLogEntity>,
        activitySessions: List<ActivitySessionEntity>,
        todayHealthLogs: List<HealthLogEntity>,
        todayFoodLogs: List<FoodLogEntity>,
        muscleSuggestion: Muscle?
    ): List<LocalRecommendation> {
        val lowSleep = todayHealthLogs.any { it.sleepDurationHours in 0.1f..5.5f || (it.type == "sleep" && it.value in 0.1f..5.5f) }
        if (lowSleep) {
            return listOf(
                LocalRecommendation(
                    title = "Sesion suave",
                    reason = "Te sugerimos sesion suave porque registraste poco sueno.",
                    actionLabel = "Ver rutina suave",
                    routeHint = "workout"
                )
            )
        }

        val latestWorkout = workouts.maxByOrNull { it.timestamp }
        val daysSinceWorkout = latestWorkout?.let { daysSince(it.timestamp) }
        if (daysSinceWorkout == null || daysSinceWorkout >= 2) {
            return listOf(
                LocalRecommendation(
                    title = if (discipline == UserDiscipline.RUNNING) "Carrera suave" else if (discipline == UserDiscipline.CYCLING) "Rodada base" else "Rutina corta",
                    reason = "No vemos entrenamiento reciente. Una sesion de 15 a 30 min ayuda a retomar sin presion.",
                    actionLabel = "Empezar",
                    routeHint = when (discipline) {
                        UserDiscipline.RUNNING -> "active_run"
                        UserDiscipline.CYCLING -> "active_ride"
                        UserDiscipline.WELLNESS -> "wellness_diary"
                        else -> "workout"
                    }
                )
            )
        }

        val waterLow = todayHealthLogs.sumOf { it.waterIntakeMl } in 1..999
        val foodMissing = todayFoodLogs.isEmpty() && goal.contains("grasa", ignoreCase = true)
        val suggestions = mutableListOf<LocalRecommendation>()
        if (waterLow) {
            suggestions.add(LocalRecommendation("Agua pendiente", "Tu registro de agua va bajo hoy.", "Registrar agua", "health"))
        }
        if (foodMissing) {
            suggestions.add(LocalRecommendation("Registra una comida", "Tu meta depende de constancia, no de exactitud perfecta.", "Agregar comida", "nutrition"))
        }
        if (muscleSuggestion != null) {
            suggestions.add(LocalRecommendation("Balance muscular", "Te sugerimos ${muscleSuggestion.displayName} porque tu balance muscular muestra menor volumen.", "Entrenar", "workout"))
        }
        return if (suggestions.isNotEmpty()) suggestions else listOf(
            LocalRecommendation("Plan simple de hoy", disciplineDefaultReason(discipline), disciplineDefaultAction(discipline), disciplineDefaultRoute(discipline))
        )
    }

    private fun disciplineDefaultReason(discipline: UserDiscipline): String = when (discipline) {
        UserDiscipline.RUNNING -> "Mantén una carrera suave si te sientes bien, o descanso si hay fatiga."
        UserDiscipline.CYCLING -> "Una rodada base suma volumen sin exigir intensidad alta."
        UserDiscipline.WALKING -> "Un paseo ligero ayuda a despejar la mente y sumar pasos sin carga."
        UserDiscipline.WELLNESS -> "Un check-in de agua, energia y sueno mantiene tu racha."
        UserDiscipline.HYBRID -> "Combina una accion corta de fuerza, cardio o bienestar."
        UserDiscipline.GYM -> "Elige una rutina por tiempo disponible y registra tus series."
    }

    private fun disciplineDefaultAction(discipline: UserDiscipline): String = when (discipline) {
        UserDiscipline.RUNNING -> "Correr"
        UserDiscipline.CYCLING -> "Rodar"
        UserDiscipline.WALKING -> "Caminar"
        UserDiscipline.WELLNESS -> "Check-in"
        UserDiscipline.HYBRID -> "Ver plan"
        UserDiscipline.GYM -> "Entrenar"
    }

    private fun disciplineDefaultRoute(discipline: UserDiscipline): String = when (discipline) {
        UserDiscipline.RUNNING -> "active_run"
        UserDiscipline.CYCLING -> "active_ride"
        UserDiscipline.WALKING -> "active_walk"
        UserDiscipline.WELLNESS -> "wellness_diary"
        UserDiscipline.HYBRID -> "daily_plan"
        UserDiscipline.GYM -> "workout"
    }

    private fun daysSince(timestamp: Long): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((start - timestamp) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
    }
}
