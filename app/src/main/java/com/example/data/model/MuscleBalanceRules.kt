package com.example.data.model

data class MusclePair(
    val name: String,
    val a: Muscle,
    val b: Muscle,
    val idealRatio: Float
)

object MuscleBalanceRules {
    val ANTAGONIST_PAIRS = listOf(
        MusclePair("Empuje / jalón", Muscle.CHEST, Muscle.BACK, 1.0f),
        MusclePair("Cuádriceps / femorales", Muscle.QUADS, Muscle.HAMSTRINGS, 1.5f),
        MusclePair("Bíceps / tríceps", Muscle.BICEPS, Muscle.TRICEPS, 1.0f),
        MusclePair("Hombros / espalda", Muscle.SHOULDERS, Muscle.BACK, 0.7f),
        MusclePair("Glúteos / cuádriceps", Muscle.GLUTES, Muscle.QUADS, 0.9f)
    )
}
