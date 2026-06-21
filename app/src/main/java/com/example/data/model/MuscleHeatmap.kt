package com.example.data.model

data class MuscleIntensity(
    val muscleGroup: Muscle,
    val intensity: Int,
    val lastTrained: String?,
    val sessions: Int,
    val totalVolume: Float
)

data class MuscleImbalance(
    val pair: String,
    val weakMuscle: Muscle,
    val strongMuscle: Muscle,
    val severity: String,
    val recommendation: String
)

data class MuscleRecommendation(
    val muscle: Muscle,
    val reason: String,
    val urgency: String
)

enum class BodyModel {
    MALE,
    FEMALE
}

fun intensityToColor(intensity: Int): Long = when {
    intensity <= 0 -> 0xFF334155
    intensity <= 25 -> 0xFFFACC15
    intensity <= 50 -> 0xFFFB923C
    intensity <= 75 -> 0xFFF97316
    else -> 0xFFE11D48
}
