package com.example.data.model

data class TrainingPlanDefaults(
    val equipment: String,
    val intensity: String,
    val weeklyGoal: String
)

object TrainingPlanRules {
    fun defaults(
        level: String,
        trainingDays: String,
        trainingPlace: String
    ): TrainingPlanDefaults = TrainingPlanDefaults(
        equipment = equipmentFor(trainingPlace),
        intensity = intensityFor(level),
        weeklyGoal = weeklyGoalFor(trainingDays)
    )

    fun intensityFor(level: String): String = when (level.trim().lowercase()) {
        "avanzado" -> "Alta"
        "intermedio" -> "Moderada"
        else -> "Ligera"
    }

    fun equipmentFor(trainingPlace: String): String = when (trainingPlace.trim().lowercase()) {
        "casa" -> "Peso corporal y equipo básico"
        "gimnasio" -> "Máquinas, barras y mancuernas"
        else -> "Peso corporal y equipo de gimnasio"
    }

    fun weeklyGoalFor(trainingDays: String): String {
        val days = trainingDays.toIntOrNull()?.coerceIn(1, 7) ?: 3
        return "Completar $days entrenamientos"
    }
}
