package com.example.data.model

object MuscleTrainingProfiles {
    val ALL_MUSCLES = listOf(
        Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS,
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES,
        Muscle.ABS, Muscle.FOREARMS, Muscle.TRAPS, Muscle.ADDUCTORS, Muscle.ABDUCTORS
    )

    val FUNCTIONAL_STRENGTH_PROFILE = listOf(
        Muscle.QUADS to 0.80f,
        Muscle.GLUTES to 0.75f,
        Muscle.BACK to 0.70f,
        Muscle.CHEST to 0.65f,
        Muscle.SHOULDERS to 0.55f,
        Muscle.ABS to 0.45f
    )

    val CARDIO_PROFILE = listOf(
        Muscle.CALVES to 0.75f,
        Muscle.QUADS to 0.65f,
        Muscle.GLUTES to 0.40f
    )

    val MOBILITY_PROFILE = listOf(
        Muscle.ABS to 0.65f,
        Muscle.BACK to 0.55f,
        Muscle.SHOULDERS to 0.45f,
        Muscle.HAMSTRINGS to 0.30f
    )

    val GENERAL_TRAINING_PROFILE = listOf(
        Muscle.QUADS to 0.55f,
        Muscle.GLUTES to 0.50f,
        Muscle.BACK to 0.50f,
        Muscle.CHEST to 0.45f,
        Muscle.ABS to 0.45f
    )

    val EXERCISE_MUSCLE_MAP = mapOf(
        "sentadilla" to listOf(Muscle.QUADS to 1.0f, Muscle.GLUTES to 0.8f, Muscle.HAMSTRINGS to 0.4f),
        "sentadilla bulgara" to listOf(Muscle.GLUTES to 1.0f, Muscle.QUADS to 0.8f, Muscle.HAMSTRINGS to 0.5f),
        "peso muerto" to listOf(Muscle.HAMSTRINGS to 1.0f, Muscle.GLUTES to 0.9f, Muscle.BACK to 0.7f),
        "zancadas" to listOf(Muscle.QUADS to 0.9f, Muscle.GLUTES to 0.8f),
        "split squat" to listOf(Muscle.QUADS to 0.85f, Muscle.GLUTES to 0.75f),
        "hip thrust" to listOf(Muscle.GLUTES to 1.0f, Muscle.HAMSTRINGS to 0.5f),
        "press banca" to listOf(Muscle.CHEST to 1.0f, Muscle.TRICEPS to 0.6f, Muscle.SHOULDERS to 0.4f),
        "dominadas" to listOf(Muscle.BACK to 1.0f, Muscle.BICEPS to 0.7f),
        "remo" to listOf(Muscle.BACK to 1.0f, Muscle.BICEPS to 0.55f),
        "press militar" to listOf(Muscle.SHOULDERS to 1.0f, Muscle.TRICEPS to 0.5f),
        "curl" to listOf(Muscle.BICEPS to 1.0f, Muscle.FOREARMS to 0.4f),
        "triceps" to listOf(Muscle.TRICEPS to 1.0f),
        "plancha" to listOf(Muscle.ABS to 1.0f),
        "crunch" to listOf(Muscle.ABS to 1.0f)
    )
}
