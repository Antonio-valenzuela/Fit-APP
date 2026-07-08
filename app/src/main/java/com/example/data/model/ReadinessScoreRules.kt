package com.example.data.model

data class ReadinessScore(
    val score: Int,
    val level: String,
    val label: String,
    val message: String,
    val reasons: List<String>
)

object ReadinessScoreRules {
    private const val LOW_CONFIDENCE_SCORE = 55

    fun calculate(monitor: HealthActivityMonitor): ReadinessScore {
        if (monitor.dataConfidence == DataConfidence.LOW) {
            return ReadinessScore(
                score = LOW_CONFIDENCE_SCORE,
                level = "Base inicial",
                label = "$LOW_CONFIDENCE_SCORE/100 Base inicial",
                message = "Necesitamos más registros para darte una recomendación más precisa.",
                reasons = listOf("Faltan registros de agua, comida, peso o descanso para afinar el plan.")
            )
        }

        var score = 70
        val reasons = mutableListOf<String>()

        when (monitor.hydrationSignal) {
            HydrationSignal.OK -> {
                score += 8
                reasons.add("Tu hidratación va bien hoy.")
            }
            HydrationSignal.LOW -> {
                score -= 12
                reasons.add("Tu hidratación va baja.")
            }
            HydrationSignal.MISSING -> {
                score -= 6
                reasons.add("Falta registrar agua.")
            }
        }

        when {
            monitor.mealCount >= 2 -> {
                score += 6
                reasons.add("Ya tienes comidas registradas.")
            }
            monitor.mealCount == 1 -> {
                score += 2
                reasons.add("Ya registraste una comida.")
            }
            else -> {
                score -= 6
                reasons.add("Falta registrar comida.")
            }
        }

        when (val days = monitor.daysSinceLastTraining) {
            null -> {
                score -= 8
                reasons.add("Aún no hay entrenamientos registrados.")
            }
            0 -> reasons.add("Entrenaste hoy; escucha tu recuperación.")
            in 1..3 -> {
                score += 8
                reasons.add("Tu actividad reciente es constante.")
            }
            in 4..6 -> {
                score -= 8
                reasons.add("Llevas varios días sin actividad.")
            }
            else -> {
                score -= 15
                reasons.add("Hace $days días no registras entrenamiento.")
            }
        }

        monitor.sleepHours?.let { sleep ->
            when {
                sleep < 5.5f -> {
                    score -= 14
                    reasons.add("Dormiste poco.")
                }
                sleep < 6.5f -> {
                    score -= 8
                    reasons.add("Tu descanso fue limitado.")
                }
                sleep >= 7f -> {
                    score += 10
                    reasons.add("Tu descanso apoya el entrenamiento.")
                }
            }
        }

        when {
            monitor.energyLevel >= 7 -> {
                score += 8
                reasons.add("Tu energía registrada es buena.")
            }
            monitor.energyLevel in 1..3 -> {
                score -= 10
                reasons.add("Registraste energía baja.")
            }
            monitor.energyLevel in 4..5 -> {
                score -= 5
                reasons.add("Tu energía está moderada.")
            }
        }

        when {
            monitor.fatigueLevel >= 7 -> {
                score -= 16
                reasons.add("Registraste fatiga alta.")
            }
            monitor.fatigueLevel in 4..6 -> {
                score -= 8
                reasons.add("Hay fatiga moderada.")
            }
        }

        when (monitor.overtrainingRisk) {
            OvertrainingRisk.HIGH -> {
                score -= 18
                reasons.add("La carga reciente sugiere bajar intensidad.")
            }
            OvertrainingRisk.MODERATE -> {
                score -= 8
                reasons.add("La carga reciente pide cuidado.")
            }
            OvertrainingRisk.LOW -> Unit
        }

        if (monitor.neglectedMuscles.isNotEmpty()) {
            score -= 2
            reasons.add("Hay músculos que conviene equilibrar.")
        }

        val finalScore = score.coerceIn(20, 100)
        val level = when {
            finalScore >= 78 -> "Listo"
            finalScore >= 60 -> "Moderado"
            finalScore >= 46 -> "Ligero"
            else -> "Recuperación"
        }
        return ReadinessScore(
            score = finalScore,
            level = level,
            label = "$finalScore/100 $level",
            message = messageFor(finalScore, monitor),
            reasons = reasons.ifEmpty { listOf("Usamos tus registros locales recientes.") }
        )
    }

    private fun messageFor(score: Int, monitor: HealthActivityMonitor): String = when {
        monitor.overtrainingRisk == OvertrainingRisk.HIGH || monitor.fatigueLevel >= 7 ->
            "Hoy conviene recuperación activa o una sesión muy suave. Tu cuerpo muestra señales de carga."
        monitor.hydrationSignal == HydrationSignal.LOW ->
            "Primero hidrátate y elige una sesión moderada si te sientes bien."
        score < 46 ->
            "Hoy conviene recuperación activa o una sesión muy suave. Tu cuerpo muestra señales de carga."
        monitor.hasLowActivity ->
            "Retoma con una sesión corta y cómoda. La meta hoy es volver al ritmo."
        score >= 78 ->
            "Pinta como buen día para entrenar con intención, manteniendo técnica y control."
        else ->
            "Una sesión moderada es buena opción hoy. Ajusta intensidad según cómo te sientas."
    }
}
