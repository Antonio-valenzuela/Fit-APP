package com.example.ui.components

object MotivationMessageProvider {
    fun getMessage(
        currentStreak: Int,
        missionsCompleted: Int,
        totalMissions: Int,
        hasCheckedInToday: Boolean,
        softMotivationMode: Boolean = true
    ): String {
        if (!softMotivationMode) {
            // High energy tone
            return when {
                missionsCompleted == totalMissions && totalMissions > 0 -> {
                    "Increible. Completaste todas tus metas de hoy."
                }
                currentStreak >= 10 -> {
                    "Llevas un gran ritmo: $currentStreak días de constancia."
                }
                currentStreak in 4..9 -> {
                    "Racha de $currentStreak días. Mantener el ritmo ya es una victoria."
                }
                hasCheckedInToday -> {
                    "Check-in listo por hoy. Buen paso para sostener tus hábitos."
                }
                else -> {
                    "El reto de hoy puede ser pequeño: registra una acción saludable."
                }
            }
        }

        // Soft, empathetic, non-guilt wellness tone (Modo Motivación Suave)
        return when {
            missionsCompleted == totalMissions && totalMissions > 0 -> {
                "Muy buen trabajo hoy. Has cumplido tus metas sin presiones. Disfruta tu descanso."
            }
            currentStreak >= 10 -> {
                "Qué alegría ver tu constancia. Sumas ya $currentStreak días cuidando de ti paso a paso."
            }
            currentStreak in 3..9 -> {
                "Vas a un ritmo saludable de $currentStreak días. Cada pequeño esfuerzo cuenta."
            }
            currentStreak == 0 -> {
                "Hoy puede ser un nuevo comienzo. Sin culpas: respira y retoma cuando puedas."
            }
            hasCheckedInToday -> {
                "Tu check-in de hoy está listo. Escuchar a tu cuerpo también es progreso."
            }
            else -> {
                "Un paso pequeno tambien cuenta. No necesitas hacerlo perfecto, solo empezar."
            }
        }
    }
}

