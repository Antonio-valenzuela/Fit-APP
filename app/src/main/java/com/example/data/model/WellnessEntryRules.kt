package com.example.data.model

import kotlin.math.roundToInt

enum class WellnessEntryType(val key: String, val label: String) {
    WATER("water", "Agua"),
    FOOD("food", "Comida"),
    WEIGHT("weight", "Peso"),
    GLUCOSE("glucose", "Glucosa"),
    PRESSURE("pressure", "Presion"),
    SLEEP("sleep", "Sueno"),
    ENERGY("energy", "Energia"),
    STEPS("steps", "Pasos"),
    HABIT("habit", "Check-in")
}

data class WellnessEntryDraft(
    val type: WellnessEntryType,
    val valueText: String = "",
    val secondaryValueText: String = "",
    val unit: String = "",
    val context: String = "",
    val note: String = "",
    val pulseText: String = ""
)

data class WellnessValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

data class TodayWellnessSummary(
    val waterMl: Int,
    val waterProgress: Float,
    val waterStatus: String,
    val latestEnergyText: String,
    val latestSleepText: String,
    val stepsText: String,
    val latestGlucoseText: String,
    val latestPressureText: String,
    val checkInText: String
)

object WellnessEntryRules {
    const val DAILY_WATER_GOAL_ML = 2000
    const val AUTO_WORKOUT_CONTEXT = "entrenamiento"

    fun validate(draft: WellnessEntryDraft): WellnessValidationResult {
        val errors = mutableListOf<String>()
        val value = draft.valueText.trim().toFloatOrNull()
        val secondary = draft.secondaryValueText.trim().toFloatOrNull()
        val pulse = draft.pulseText.trim().toIntOrNull()
        val hasNote = draft.note.trim().isNotEmpty()
        val hasContext = draft.context.trim().isNotEmpty()

        fun requirePositive(message: String) {
            if (value == null || value <= 0f) errors.add(message)
        }

        when (draft.type) {
            WellnessEntryType.WATER -> requirePositive("Ingresa mililitros de agua mayores a 0.")
            WellnessEntryType.WEIGHT -> requirePositive("Ingresa un peso mayor a 0.")
            WellnessEntryType.GLUCOSE -> requirePositive("Ingresa glucosa en mg/dL mayor a 0.")
            WellnessEntryType.STEPS -> requirePositive("Ingresa pasos mayores a 0.")
            WellnessEntryType.SLEEP -> requirePositive("Ingresa horas de sueno mayores a 0.")
            WellnessEntryType.ENERGY -> requirePositive("Selecciona energia de 1 a 5.")
            WellnessEntryType.PRESSURE -> {
                if (value == null || value <= 0f) errors.add("Ingresa presion sistolica.")
                if (secondary == null || secondary <= 0f) errors.add("Ingresa presion diastolica.")
                if (draft.pulseText.isNotBlank() && (pulse == null || pulse <= 0)) {
                    errors.add("El pulso debe ser un numero positivo.")
                }
            }
            WellnessEntryType.FOOD -> {
                if (!hasContext && value == null && !hasNote) {
                    errors.add("Agrega una porcion visual, calorias o una nota de comida.")
                }
                if (value != null && value < 0f) errors.add("Las calorias no pueden ser negativas.")
            }
            WellnessEntryType.HABIT -> {
                if (!hasContext && !hasNote) errors.add("Agrega el habito o check-in que quieres registrar.")
            }
        }

        if (value != null && value < 0f) errors.add("No se permiten valores negativos.")
        if (secondary != null && secondary < 0f) errors.add("No se permiten valores secundarios negativos.")
        return WellnessValidationResult(errors.isEmpty(), errors.distinct())
    }

    fun buildTodaySummary(entries: List<HealthLogEntity>, hasWorkoutToday: Boolean): TodayWellnessSummary {
        val sorted = entries.sortedByDescending { it.timestamp }
        val totalWater = entries.sumOf { it.waterMl() }
        val waterProgress = (totalWater / DAILY_WATER_GOAL_ML.toFloat()).coerceIn(0f, 1f)
        val waterStatus = if (totalWater >= DAILY_WATER_GOAL_ML) {
            "Meta cumplida"
        } else {
            "${(waterProgress * 100).roundToInt()}% de 2 L"
        }

        val latestEnergy = sorted.firstOrNull { it.energyLevel > 0 || it.type == WellnessEntryType.ENERGY.key }
        val latestSleep = sorted.firstOrNull { it.sleepDurationHours > 0f || it.type == WellnessEntryType.SLEEP.key }
        val latestGlucose = sorted.firstOrNull { it.glucoseMl() > 0f }
        val latestPressure = sorted.firstOrNull { it.systolicPressure > 0 || it.type == WellnessEntryType.PRESSURE.key }
        val totalSteps = entries.sumOf { it.steps.coerceAtLeast(if (it.type == WellnessEntryType.STEPS.key) it.value.roundToInt() else 0) }
        val hasWorkoutEntry = hasWorkoutToday || entries.any { it.isAutoWorkout || it.context == AUTO_WORKOUT_CONTEXT }

        return TodayWellnessSummary(
            waterMl = totalWater,
            waterProgress = waterProgress,
            waterStatus = waterStatus,
            latestEnergyText = latestEnergy?.let { "Energia: ${it.energyLevel.coerceAtLeast(it.value.roundToInt())}/5" } ?: "Haz check-in",
            latestSleepText = latestSleep?.let { "Sueno: ${it.sleepHours()} h" } ?: "Sin registro",
            stepsText = if (totalSteps > 0) "Pasos: $totalSteps" else "Movimiento diario",
            latestGlucoseText = latestGlucose?.let { "Glucosa: ${it.glucoseMl().formatClean()} mg/dL (${it.context.ifBlank { if (it.glucoseIsPreMeal) "antes de comer" else "despues de comer" }})" } ?: "Sin registro",
            latestPressureText = latestPressure?.let {
                val sys = it.systolicPressure.coerceAtLeast(it.value.roundToInt())
                val dia = it.diastolicPressure.coerceAtLeast(it.secondaryValue.roundToInt())
                val pulseText = if (it.pulse > 0) " pulso ${it.pulse}" else ""
                "Presion: $sys/$dia mmHg$pulseText"
            } ?: "Sin registro",
            checkInText = if (hasWorkoutEntry) "Entrenamiento registrado automaticamente" else "Sin entrenamiento registrado"
        )
    }

    fun describeEntry(log: HealthLogEntity): String {
        return when (log.resolvedType()) {
            WellnessEntryType.WATER -> "Agua: ${log.waterMl()} ml${log.context.ifBlank { "" }.let { if (it.isBlank()) "" else " ($it)" }}"
            WellnessEntryType.FOOD -> {
                val calories = if (log.value > 0f) " ${log.value.roundToInt()} kcal" else ""
                "Comida: ${log.context.ifBlank { "porcion visual" }}$calories${log.note.suffixNote()}"
            }
            WellnessEntryType.GLUCOSE -> "Glucosa: ${log.glucoseMl().formatClean()} mg/dL (${log.context.ifBlank { if (log.glucoseIsPreMeal) "antes de comer" else "despues de comer" }})"
            WellnessEntryType.PRESSURE -> {
                val sys = log.systolicPressure.coerceAtLeast(log.value.roundToInt())
                val dia = log.diastolicPressure.coerceAtLeast(log.secondaryValue.roundToInt())
                "Presion: $sys/$dia mmHg${if (log.pulse > 0) " pulso ${log.pulse}" else ""}${log.note.suffixNote()}"
            }
            WellnessEntryType.SLEEP -> "Sueno: ${log.sleepHours()} h${if (log.sleepQualityScale > 0) " calidad ${log.sleepQualityScale}/5" else ""}${log.note.suffixNote()}"
            WellnessEntryType.ENERGY -> "Energia: ${log.energyLevel.coerceAtLeast(log.value.roundToInt())}/5${log.note.suffixNote()}"
            WellnessEntryType.STEPS -> "Pasos: ${log.steps.coerceAtLeast(log.value.roundToInt())}"
            WellnessEntryType.WEIGHT -> "Peso: ${log.weightKg.coerceAtLeast(log.value).formatClean()} kg"
            WellnessEntryType.HABIT -> if (log.isAutoWorkout) "Entrenamiento registrado automaticamente" else "Check-in: ${log.context.ifBlank { log.note }}"
        }
    }

    fun HealthLogEntity.resolvedType(): WellnessEntryType {
        WellnessEntryType.entries.firstOrNull { it.key == type }?.let { return it }
        return when {
            waterIntakeMl > 0 -> WellnessEntryType.WATER
            glucoseLevel > 0f -> WellnessEntryType.GLUCOSE
            systolicPressure > 0 -> WellnessEntryType.PRESSURE
            sleepDurationHours > 0f -> WellnessEntryType.SLEEP
            energyLevel > 0 -> WellnessEntryType.ENERGY
            steps > 0 -> WellnessEntryType.STEPS
            weightKg > 0f -> WellnessEntryType.WEIGHT
            else -> WellnessEntryType.HABIT
        }
    }

    private fun HealthLogEntity.waterMl(): Int = waterIntakeMl.coerceAtLeast(if (type == WellnessEntryType.WATER.key) value.roundToInt() else 0)
    private fun HealthLogEntity.glucoseMl(): Float = glucoseLevel.coerceAtLeast(if (type == WellnessEntryType.GLUCOSE.key) value else 0f)
    private fun HealthLogEntity.sleepHours(): Float = sleepDurationHours.coerceAtLeast(if (type == WellnessEntryType.SLEEP.key) value else 0f)
    private fun Float.formatClean(): String = if (this % 1f == 0f) this.roundToInt().toString() else String.format("%.1f", this)
    private fun String.suffixNote(): String = if (isBlank()) "" else " - ${trim()}"
}
