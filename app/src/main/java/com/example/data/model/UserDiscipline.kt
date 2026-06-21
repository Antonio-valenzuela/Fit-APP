package com.example.data.model

import java.util.Locale

enum class UserDiscipline(
    val label: String,
    val description: String,
    val primaryRoute: String,
    val actionLabel: String,
    val accentColorHex: String
) {
    GYM(
        label = "Gym / fuerza",
        description = "Rutinas, series, reps, carga y balance muscular.",
        primaryRoute = "workout",
        actionLabel = "Iniciar entrenamiento",
        accentColorHex = "#3B5BDB"
    ),
    RUNNING(
        label = "Running",
        description = "Carreras, distancia, tiempo y ritmo promedio.",
        primaryRoute = "active_run",
        actionLabel = "Iniciar carrera",
        accentColorHex = "#22C55E"
    ),
    WALKING(
        label = "Caminata",
        description = "Paseos, pasos, distancia y ritmo relajado.",
        primaryRoute = "active_walk",
        actionLabel = "Iniciar caminata",
        accentColorHex = "#06B6D4"
    ),
    CYCLING(
        label = "Bicicleta",
        description = "Rodadas, distancia, tiempo y velocidad promedio.",
        primaryRoute = "active_ride",
        actionLabel = "Iniciar rodada",
        accentColorHex = "#F59E0B"
    ),
    WELLNESS(
        label = "Bienestar",
        description = "Sueno, agua, peso, energia y check-in diario.",
        primaryRoute = "wellness_diary",
        actionLabel = "Registrar bienestar",
        accentColorHex = "#A855F7"
    ),
    HYBRID(
        label = "Hibrido",
        description = "Fuerza, cardio y bienestar en un plan diario.",
        primaryRoute = "daily_plan",
        actionLabel = "Ver plan del dia",
        accentColorHex = "#FF6B35"
    );

    companion object {
        fun fromStoredName(value: String?): UserDiscipline {
            return entries.firstOrNull { it.name == value } ?: GYM
        }
    }
}

data class ActivitySessionSummary(
    val sessionCount: Int,
    val totalDistanceKm: Float,
    val totalDurationMinutes: Int,
    val hasDistance: Boolean,
    val distanceText: String?,
    val paceText: String?,
    val speedText: String?,
    val sessionCountText: String
)

object ActivitySessionSummaryRules {
    fun summarize(sessions: List<ActivitySessionEntity>): ActivitySessionSummary {
        val totalDistance = sessions.sumOf { it.distanceKm.toDouble() }.toFloat()
        val totalDurationSeconds = sessions.sumOf {
            if (it.durationSeconds > 0L) it.durationSeconds else it.durationMinutes * 60L
        }
        val totalDuration = (totalDurationSeconds / 60L).toInt()
        val hasDistance = totalDistance > 0f
        val durationMinutes = totalDurationSeconds / 60.0
        val pace = if (hasDistance && totalDurationSeconds > 0L) durationMinutes / totalDistance else null
        val speed = if (hasDistance && totalDurationSeconds > 0L) totalDistance / (totalDurationSeconds / 3600.0) else null

        return ActivitySessionSummary(
            sessionCount = sessions.size,
            totalDistanceKm = totalDistance,
            totalDurationMinutes = totalDuration,
            hasDistance = hasDistance,
            distanceText = if (hasDistance) String.format(Locale.US, "%.1f km", totalDistance) else null,
            paceText = pace?.let { String.format(Locale.US, "%.1f min/km", it) },
            speedText = speed?.let { String.format(Locale.US, "%.1f km/h", it) },
            sessionCountText = if (sessions.size == 1) "1 sesion" else "${sessions.size} sesiones"
        )
    }
}
