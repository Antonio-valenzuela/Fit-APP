package com.example.data.model

data class HybridPlanSettings(
    val strengthDaysPerWeek: Int = 3,
    val cardioDaysPerWeek: Int = 2,
    val objective: String = "Recomposicion",
    val level: String = "Principiante",
    val sessionMinutes: Int = 45,
    val runningEnabled: Boolean = true,
    val walkingEnabled: Boolean = true,
    val cyclingEnabled: Boolean = true
)
