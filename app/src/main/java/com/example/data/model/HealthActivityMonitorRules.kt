package com.example.data.model

import java.text.Normalizer
import java.util.Locale

enum class HydrationSignal {
    MISSING,
    LOW,
    OK
}

enum class MissingRecord {
    WATER,
    FOOD,
    WEIGHT,
    SLEEP_OR_ENERGY
}

enum class OvertrainingRisk {
    LOW,
    MODERATE,
    HIGH
}

enum class DataConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class HealthActivityMonitor(
    val waterMl: Int,
    val mealCount: Int,
    val hydrationSignal: HydrationSignal,
    val missingRecords: Set<MissingRecord>,
    val lastTrainingAt: Long?,
    val daysSinceLastTraining: Int?,
    val hasLowActivity: Boolean,
    val trainingSessionsLast7Days: Int,
    val recentlyWorkedMuscles: Set<Muscle>,
    val neglectedMuscles: Set<Muscle>,
    val overtrainingRisk: OvertrainingRisk,
    val sleepHours: Float?,
    val fatigueLevel: Int,
    val energyLevel: Int,
    val latestWeightKg: Float?,
    val dataConfidence: DataConfidence
)

object HealthActivityMonitorRules {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val RECENT_MUSCLE_DAYS = 14
    private const val RECENT_TRAINING_DAYS = 7

    fun build(
        todayHealthLogs: List<HealthLogEntity>,
        todayFoodLogs: List<FoodLogEntity>,
        allHealthLogs: List<HealthLogEntity>,
        workouts: List<WorkoutLogEntity>,
        activitySessions: List<ActivitySessionEntity>,
        recommendedMuscle: Muscle?,
        discipline: UserDiscipline,
        nowMillis: Long = System.currentTimeMillis()
    ): HealthActivityMonitor {
        val waterMl = todayHealthLogs.sumOf { it.waterIntakeMl }
        val hydrationSignal = when {
            waterMl <= 0 -> HydrationSignal.MISSING
            waterMl < 1500 -> HydrationSignal.LOW
            else -> HydrationSignal.OK
        }
        val latestTrainingAt = latestTrainingTimestamp(workouts, activitySessions)
        val daysSinceLastTraining = latestTrainingAt?.let { daysBetween(it, nowMillis) }
        val recentCutoff = nowMillis - (RECENT_TRAINING_DAYS * DAY_MS)
        val trainingSessionsLast7Days =
            workouts.count { it.timestamp >= recentCutoff } +
                activitySessions.count { sessionTimestamp(it) >= recentCutoff }
        val healthHistory = (todayHealthLogs + allHealthLogs).sortedByDescending { it.timestamp }
        val sleepHours = latestPositive(healthHistory.map { it.sleepDurationHours })
        val fatigueLevel = latestPositiveInt(healthHistory.map { it.painFatigueLevel })
        val energyLevel = latestPositiveInt(healthHistory.map { it.energyLevel })
        val latestWeightKg = latestPositive(healthHistory.map { it.weightKg })
        val missingRecords = buildSet {
            if (hydrationSignal == HydrationSignal.MISSING) add(MissingRecord.WATER)
            if (todayFoodLogs.isEmpty()) add(MissingRecord.FOOD)
            if (latestWeightKg == null) add(MissingRecord.WEIGHT)
            if (sleepHours == null && fatigueLevel <= 0 && energyLevel <= 0) add(MissingRecord.SLEEP_OR_ENERGY)
        }
        val recentlyWorkedMuscles = recentWorkoutMuscles(workouts, activitySessions, nowMillis)
        val neglectedMuscles = buildSet {
            recommendedMuscle?.takeIf { it !in recentlyWorkedMuscles }?.let(::add)
            if (isStrengthDiscipline(discipline) && recentlyWorkedMuscles.isNotEmpty()) {
                val basicLowerBody = setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES)
                if (recentlyWorkedMuscles.none { it in basicLowerBody }) add(Muscle.QUADS)
            }
        }
        val hasLowActivity = daysSinceLastTraining == null || daysSinceLastTraining >= 3
        val overtrainingRisk = overtrainingRisk(
            trainingSessionsLast7Days = trainingSessionsLast7Days,
            sleepHours = sleepHours,
            fatigueLevel = fatigueLevel,
            energyLevel = energyLevel
        )

        return HealthActivityMonitor(
            waterMl = waterMl,
            mealCount = todayFoodLogs.size,
            hydrationSignal = hydrationSignal,
            missingRecords = missingRecords,
            lastTrainingAt = latestTrainingAt,
            daysSinceLastTraining = daysSinceLastTraining,
            hasLowActivity = hasLowActivity,
            trainingSessionsLast7Days = trainingSessionsLast7Days,
            recentlyWorkedMuscles = recentlyWorkedMuscles,
            neglectedMuscles = neglectedMuscles,
            overtrainingRisk = overtrainingRisk,
            sleepHours = sleepHours,
            fatigueLevel = fatigueLevel,
            energyLevel = energyLevel,
            latestWeightKg = latestWeightKg,
            dataConfidence = dataConfidence(missingRecords, latestTrainingAt)
        )
    }

    private fun latestTrainingTimestamp(
        workouts: List<WorkoutLogEntity>,
        activitySessions: List<ActivitySessionEntity>
    ): Long? {
        val latestWorkout = workouts.maxOfOrNull { it.timestamp }
        val latestActivity = activitySessions.maxOfOrNull(::sessionTimestamp)
        return listOfNotNull(latestWorkout, latestActivity).maxOrNull()
    }

    private fun sessionTimestamp(session: ActivitySessionEntity): Long = when {
        session.endedAt > 0L -> session.endedAt
        session.startedAt > 0L -> session.startedAt
        else -> session.timestamp
    }

    private fun daysBetween(timestamp: Long, nowMillis: Long): Int =
        ((nowMillis - timestamp).coerceAtLeast(0L) / DAY_MS).toInt()

    private fun latestPositive(values: List<Float>): Float? = values.firstOrNull { it > 0f }

    private fun latestPositiveInt(values: List<Int>): Int = values.firstOrNull { it > 0 } ?: 0

    private fun recentWorkoutMuscles(
        workouts: List<WorkoutLogEntity>,
        activitySessions: List<ActivitySessionEntity>,
        nowMillis: Long
    ): Set<Muscle> {
        val cutoff = nowMillis - (RECENT_MUSCLE_DAYS * DAY_MS)
        val muscles = workouts
            .filter { it.timestamp >= cutoff }
            .flatMap { workout ->
                detectMuscles("${workout.routineName} ${workout.exercisesText}")
            }
            .toMutableSet()

        if (activitySessions.any { sessionTimestamp(it) >= cutoff }) {
            muscles.add(Muscle.CARDIO)
        }
        return muscles
    }

    private fun detectMuscles(text: String): Set<Muscle> {
        val normalized = normalize(text)
        return buildSet {
            if (containsAny(normalized, "pecho", "chest", "banca", "press banca", "flexion")) add(Muscle.CHEST)
            if (containsAny(normalized, "espalda", "back", "remo", "jalon", "dominada")) add(Muscle.BACK)
            if (containsAny(normalized, "hombro", "shoulder", "press militar", "elevacion lateral")) add(Muscle.SHOULDERS)
            if (containsAny(normalized, "biceps", "curl")) add(Muscle.BICEPS)
            if (containsAny(normalized, "triceps", "fondos")) add(Muscle.TRICEPS)
            if (containsAny(normalized, "abdomen", "abs", "core", "plancha")) add(Muscle.ABS)
            if (containsAny(normalized, "gluteo", "gluteos", "hip thrust", "puente")) add(Muscle.GLUTES)
            if (containsAny(normalized, "sentadilla", "cuadriceps", "quads", "pierna")) add(Muscle.QUADS)
            if (containsAny(normalized, "peso muerto", "femoral", "femorales", "hamstring")) add(Muscle.HAMSTRINGS)
            if (containsAny(normalized, "pantorrilla", "calf", "gemelo")) add(Muscle.CALVES)
            if (containsAny(normalized, "full body", "cuerpo completo")) add(Muscle.FULL_BODY)
            if (containsAny(normalized, "cardio", "carrera", "rodada", "bicicleta", "caminata")) add(Muscle.CARDIO)
        }
    }

    private fun overtrainingRisk(
        trainingSessionsLast7Days: Int,
        sleepHours: Float?,
        fatigueLevel: Int,
        energyLevel: Int
    ): OvertrainingRisk {
        val lowSleep = sleepHours != null && sleepHours < 5.5f
        val moderateSleep = sleepHours != null && sleepHours < 6.2f
        val highFatigue = fatigueLevel >= 7
        val lowEnergy = energyLevel in 1..3

        return when {
            trainingSessionsLast7Days >= 5 && (lowSleep || highFatigue || lowEnergy) -> OvertrainingRisk.HIGH
            trainingSessionsLast7Days >= 6 -> OvertrainingRisk.HIGH
            trainingSessionsLast7Days >= 4 && (moderateSleep || fatigueLevel >= 6 || lowEnergy) -> OvertrainingRisk.MODERATE
            trainingSessionsLast7Days >= 5 -> OvertrainingRisk.MODERATE
            else -> OvertrainingRisk.LOW
        }
    }

    private fun dataConfidence(missingRecords: Set<MissingRecord>, latestTrainingAt: Long?): DataConfidence = when {
        missingRecords.size >= 3 && latestTrainingAt == null -> DataConfidence.LOW
        missingRecords.size >= 3 -> DataConfidence.MEDIUM
        missingRecords.size <= 1 -> DataConfidence.HIGH
        else -> DataConfidence.MEDIUM
    }

    private fun isStrengthDiscipline(discipline: UserDiscipline): Boolean =
        discipline == UserDiscipline.GYM || discipline == UserDiscipline.HYBRID

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }
}
