package com.example.data.api

import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import java.text.SimpleDateFormat
import java.util.Locale

object FitCoreAiEngine {

    fun buildCoachPrompt(
        user: UserEntity,
        recentVitals: List<HealthLogEntity>,
        requestType: String
    ): String {
        val mode = normalizeRequestType(requestType)
        val tdee = estimateTdee(user)
        val bmr = estimateBmr(user)
        val hydrationLiters = estimateHydrationLiters(user)
        val vitalsSummary = recentVitals.take(8).joinToString("; ") { log ->
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(log.timestamp)
            "Fecha: $date, peso: ${valueOrMissing(log.weightKg, "kg")}, agua: ${log.waterIntakeMl} ml, " +
                "sueño: ${valueOrMissing(log.sleepDurationHours, "h")} calidad ${log.sleepQualityScale}/10, " +
                "presión: ${if (log.systolicPressure > 0) "${log.systolicPressure}/${log.diastolicPressure}" else "sin registro"}, " +
                "glucosa: ${valueOrMissing(log.glucoseLevel, "mg/dL")}, energia: ${log.energyLevel}/10, " +
                "animo: ${log.moodLevel}/10, fatiga/dolor: ${log.painFatigueLevel}/10, pasos: ${log.steps}"
        }.ifBlank { "Sin registros recientes suficientes." }

        return """
            Eres FitCore AI, el motor inteligente de FitPlan MX.

            Identidad:
            - Entrenador personal, nutriólogo básico y coach de bienestar.
            - Español claro, motivador y directo.
            - Sin condescendencia, sin alarmismo y sin diagnósticos médicos.
            - Si hay dolor, lesión o síntoma preocupante: recomienda pausar el ejercicio afectado y consultar a un médico o fisioterapeuta.

            Perfil dinámico:
            Nombre: ${user.name.ifBlank { "Usuario" }}
            Objetivo principal: ${user.goal}
            Nivel estimado: ${inferTrainingLevel(user)}
            Días disponibles: no especificado; sugiere una base flexible de 3-4 días si falta información.
            Equipamiento: no especificado; ofrece alternativas de casa y gimnasio.
            Semana actual del plan: no especificada.
            Último peso registrado: ${user.weightKg} kg
            BMR estimado Mifflin-St Jeor: ${bmr.toInt()} kcal
            TDEE estimado: ${tdee.toInt()} kcal
            Calorías objetivo actuales: ${user.targetCalories} kcal
            Proteínas: ${user.targetProteinG} g, carbohidratos: ${user.targetCarbsG} g, grasas: ${user.targetFatG} g
            Hidratación sugerida: ${"%.1f".format(hydrationLiters)} L/día
            Historial reciente: $vitalsSummary

            Tarea solicitada: $mode.

            Reglas:
            - Si falta información crítica, pide solo 1-2 datos y da una recomendación provisional segura.
            - Prioriza adherencia sostenible sobre resultados rápidos.
            - Incluye una acción concreta al final.
            - Para entrenamiento, incluye calentamiento, trabajo principal, accesorio y movilidad.
            - Para nutrición, usa principios flexibles, no dietas restrictivas.
            - Si detectas déficit bajo BMR o carga excesiva, educa y ofrece alternativa segura.
            - Máximo 180 palabras, con viñetas cortas.
        """.trimIndent()
    }

    fun generateOfflineAdvice(
        user: UserEntity,
        logs: List<HealthLogEntity>,
        requestType: String
    ): String {
        val name = user.name.ifBlank { "vamos" }
        val mode = normalizeRequestType(requestType)
        val bmr = estimateBmr(user).toInt()
        val tdee = estimateTdee(user).toInt()
        val hydrationLiters = estimateHydrationLiters(user)
        val last = logs.firstOrNull()
        val sleepHint = when {
            last == null || last.sleepDurationHours <= 0f -> "Registra sueño esta noche para ajustar recuperación."
            last.sleepDurationHours < 6.5f -> "Hoy baja la intensidad y prioriza 7-9 horas de sueño."
            else -> "Tu descanso va bien; úsalo para progresar sin forzar."
        }
        val waterHint = when {
            last == null || last.waterIntakeMl <= 0 -> "Empieza registrando agua: meta sugerida ${"%.1f".format(hydrationLiters)} L."
            last.waterIntakeMl < hydrationLiters * 700 -> "Te falta hidratación: suma 1 vaso ahora y otro con tu próxima comida."
            else -> "Hidratación en buen camino."
        }
        val recoveryHint = when {
            last == null || last.energyLevel <= 0 -> "Registra energia y fatiga para ajustar la carga del plan."
            last.energyLevel <= 4 || last.painFatigueLevel >= 7 -> "Hoy conviene bajar volumen: energia baja o fatiga alta reportada."
            else -> "Tu energia permite una sesion normal, cuidando tecnica y descanso."
        }

        return when (mode) {
            "plan de entrenamiento" -> """
                FitCore AI para $name
                - Calentamiento: 5-8 min de movilidad + activación suave.
                - Trabajo principal: 3 días base esta semana; empuje, pierna y jalón/cuerpo completo.
                - Progresión: si completas todos los sets con buena técnica, sube 2-5% la próxima sesión.
                - Accesorios: core, glúteo medio y espalda alta 2 veces por semana.
                - Movilidad: 5 min al final, respiración nasal y estiramientos ligeros.
                Acción: registra tu próxima rutina para que FitCore ajuste volumen y dificultad.
            """.trimIndent()

            "recuperación y bienestar" -> """
                FitCore AI para $name
                - $sleepHint
                - $waterHint
                - $recoveryHint
                - Si hay dolor punzante o molestia que cambia tu técnica, pausa ese ejercicio y consulta a un profesional.
                - Usa descanso activo: caminata suave, movilidad de cadera/torácica y respiración 5 min.
                Acción: registra sueño, energía 1-10 y agua hoy para detectar patrones.
            """.trimIndent()

            "alertas preventivas" -> buildPreventiveSummary(name, last, hydrationLiters)

            else -> """
                FitCore AI para $name
                - BMR estimado: $bmr kcal; TDEE estimado: $tdee kcal.
                - Tu objetivo actual es ${user.targetCalories} kcal con ${user.targetProteinG} g de proteína.
                - $waterHint
                - Elige comidas simples: proteína + fibra + carbohidrato según entrenamiento + grasa saludable.
                - Evita extremos: si comes por debajo de tu BMR de forma sostenida, ajusta el déficit.
                Acción: registra una comida y tu nivel de energía para afinar tus macros.
            """.trimIndent()
        }
    }

    private fun normalizeRequestType(requestType: String): String {
        val clean = requestType.lowercase(Locale.getDefault())
        return when {
            clean.contains("plan") || clean.contains("entren") -> "plan de entrenamiento"
            clean.contains("recuper") || clean.contains("sue") || clean.contains("estr") -> "recuperación y bienestar"
            clean.contains("alert") -> "alertas preventivas"
            else -> "nutrición y hábitos"
        }
    }

    private fun buildPreventiveSummary(
        name: String,
        last: HealthLogEntity?,
        hydrationLiters: Float
    ): String {
        if (last == null) {
            return """
                FitCore AI para $name
                - Aún faltan registros para detectar tendencias.
                - Esto no sustituye una consulta médica; úsalo como guía de hábitos.
                Acción: registra agua, sueño y peso para crear tu primera línea base.
            """.trimIndent()
        }

        val notes = mutableListOf<String>()
        if (last.sleepDurationHours in 0.1f..6.4f) notes += "Dormiste poco; usa entrenamiento ligero o movilidad."
        if (last.waterIntakeMl in 1..1499) notes += "Hidratación baja; apunta a ${"%.1f".format(hydrationLiters)} L/día."
        if (last.energyLevel in 1..4 || last.painFatigueLevel >= 7) {
            notes += "Energia baja o fatiga alta; reduce volumen y evita buscar marcas personales hoy."
        }
        if (last.systolicPressure > 130 || last.diastolicPressure > 85) {
            notes += "Presión por encima de rango habitual; evita intensidad alta y consulta si se repite."
        }
        if (last.glucoseLevel > 0 && (last.glucoseLevel < 70 || last.glucoseLevel > 140)) {
            notes += "Glucosa fuera de rango común; observa tendencia y consulta si se repite."
        }

        return """
            FitCore AI para $name
            - Esto no sustituye una consulta médica; es orientación preventiva.
            ${if (notes.isEmpty()) "- Tus últimos registros no muestran señales importantes. Mantén el ritmo." else notes.joinToString("\n") { "- $it" }}
            Acción: registra tu próximo check-in para confirmar tendencia.
        """.trimIndent()
    }

    private fun estimateBmr(user: UserEntity): Float {
        val base = (10f * user.weightKg) + (6.25f * user.heightCm) - (5f * user.age)
        return if (user.gender.equals("Masculino", ignoreCase = true)) base + 5f else base - 161f
    }

    private fun estimateTdee(user: UserEntity): Float {
        val factor = when {
            user.activityLevel.contains("Activo", ignoreCase = true) -> 1.725f
            user.activityLevel.contains("Moderado", ignoreCase = true) -> 1.55f
            user.activityLevel.contains("Ligero", ignoreCase = true) -> 1.375f
            else -> 1.2f
        }
        return estimateBmr(user) * factor
    }

    private fun estimateHydrationLiters(user: UserEntity): Float {
        val base = user.weightKg * 0.035f
        val activityBonus = if (user.activityLevel.contains("Activo", ignoreCase = true)) 0.5f else 0.25f
        return (base + activityBonus).coerceIn(1.8f, 4.5f)
    }

    private fun inferTrainingLevel(user: UserEntity): String {
        return when {
            user.activityLevel.contains("Activo", ignoreCase = true) -> "intermedio"
            user.activityLevel.contains("Moderado", ignoreCase = true) -> "principiante-intermedio"
            else -> "principiante"
        }
    }

    private fun valueOrMissing(value: Float, suffix: String): String {
        return if (value > 0f) "$value $suffix" else "sin registro"
    }
}

