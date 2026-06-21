package com.example.data.report

import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HealthReportSection(
    val title: String,
    val lines: List<String>
)

data class WeeklyNutritionSummary(
    val label: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float
)

data class ReportPoint(
    val label: String,
    val value: Float
)

data class HealthReportContent(
    val coverTitle: String,
    val patientLine: String,
    val rangeLine: String,
    val planLine: String,
    val legalDisclaimer: String,
    val weightPoints: List<ReportPoint>,
    val weeklyNutrition: List<WeeklyNutritionSummary>,
    val activityItems: List<String>,
    val sections: List<HealthReportSection>
)

object HealthReportContentBuilder {
    const val LEGAL_DISCLAIMER =
        "EXENCION DE RESPONSABILIDAD: Este informe es solo para fines informativos y de seguimiento personal. No constituye asesoramiento medico, diagnostico o tratamiento. Los datos proporcionados son estimaciones del usuario y no han sido validados por profesionales medicos. Consulte siempre a un profesional de la salud antes de tomar decisiones basadas en esta informacion."

    fun build(
        user: UserEntity?,
        foodLogs: List<FoodLogEntity>,
        workoutLogs: List<WorkoutLogEntity>,
        healthLogs: List<HealthLogEntity>,
        generatedAt: Long = System.currentTimeMillis()
    ): HealthReportContent {
        val range = reportRange(generatedAt)
        val filteredFood = foodLogs.filter { it.timestamp in range.first..range.second }
        val filteredWorkouts = workoutLogs.filter { it.timestamp in range.first..range.second }
        val filteredHealth = healthLogs.filter { it.timestamp in range.first..range.second }
        val weightPoints = filteredHealth
            .filter { it.weightKg > 0f }
            .sortedBy { it.timestamp }
            .map { ReportPoint(shortDate(it.timestamp), it.weightKg) }
        val weeklyNutrition = buildWeeklyNutrition(filteredFood)
        val activityItems = filteredWorkouts
            .sortedByDescending { it.timestamp }
            .map {
                "${shortDate(it.timestamp)} - ${it.routineName}: ${it.durationMinutes} min, ${it.caloriesBurned} kcal. ${it.exercisesText}"
            }

        val waterMl = filteredHealth.sumOf { it.waterIntakeMl }
        val glucoseCount = filteredHealth.count { it.glucoseLevel > 0f }
        val pressureCount = filteredHealth.count { it.systolicPressure > 0 && it.diastolicPressure > 0 }
        val sleepAvg = filteredHealth.filter { it.sleepDurationHours > 0f }.map { it.sleepDurationHours }.averageOrNull()

        return HealthReportContent(
            coverTitle = "FitPlan MX - Informe de seguimiento personal",
            patientLine = "Usuario: ${user?.name?.ifBlank { "Sin nombre" } ?: "Sin nombre"}",
            rangeLine = "Rango: ${date(range.first)} al ${date(range.second)}",
            planLine = "Objetivo: ${user?.goal.orEmpty().ifBlank { "No especificado" }} - Meta diaria: ${user?.targetCalories ?: 0} kcal",
            legalDisclaimer = LEGAL_DISCLAIMER,
            weightPoints = weightPoints,
            weeklyNutrition = weeklyNutrition,
            activityItems = activityItems,
            sections = listOf(
                HealthReportSection(
                    title = "Resumen clinico informativo",
                    lines = listOf(
                        "Registros de peso: ${weightPoints.size}",
                        "Agua acumulada: $waterMl ml",
                        "Mediciones de glucosa: $glucoseCount",
                        "Mediciones de presion: $pressureCount",
                        "Sueno promedio: ${sleepAvg?.let { "%.1f h".format(Locale.US, it) } ?: "Sin datos"}"
                    )
                ),
                HealthReportSection(
                    title = "Graficos de progreso",
                    lines = if (weightPoints.isEmpty()) listOf("Sin registros de peso en el rango.") else weightPoints.map { "${it.label}: ${it.value} kg" }
                ),
                HealthReportSection(
                    title = "Resumen semanal de nutricion",
                    lines = if (weeklyNutrition.isEmpty()) listOf("Sin registros de nutricion en el rango.") else weeklyNutrition.map {
                        "${it.label}: ${it.calories} kcal, P ${it.proteinG.clean()} g, C ${it.carbsG.clean()} g, G ${it.fatG.clean()} g"
                    }
                ),
                HealthReportSection(
                    title = "Actividad fisica",
                    lines = if (activityItems.isEmpty()) listOf("Sin entrenamientos registrados en el rango.") else activityItems
                )
            )
        )
    }

    private fun buildWeeklyNutrition(foodLogs: List<FoodLogEntity>): List<WeeklyNutritionSummary> {
        return foodLogs
            .groupBy { weekLabel(it.timestamp) }
            .toSortedMap()
            .map { (label, logs) ->
                WeeklyNutritionSummary(
                    label = label,
                    calories = logs.sumOf { it.calories },
                    proteinG = logs.sumOf { it.proteinG.toDouble() }.toFloat(),
                    carbsG = logs.sumOf { it.carbsG.toDouble() }.toFloat(),
                    fatG = logs.sumOf { it.fatG.toDouble() }.toFloat()
                )
            }
    }

    private fun reportRange(generatedAt: Long): Pair<Long, Long> {
        val end = Calendar.getInstance().apply { timeInMillis = generatedAt }
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        val start = Calendar.getInstance().apply {
            timeInMillis = generatedAt
            add(Calendar.DAY_OF_YEAR, -29)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun weekLabel(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${calendar.get(Calendar.YEAR)}-S${calendar.get(Calendar.WEEK_OF_YEAR).toString().padStart(2, '0')}"
    }

    private fun date(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    private fun shortDate(timestamp: Long): String = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
    private fun Iterable<Float>.averageOrNull(): Double? = if (none()) null else map { it.toDouble() }.average()
    private fun Float.clean(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.US, this)
}
