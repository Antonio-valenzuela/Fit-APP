package com.example

import com.example.data.api.FitCoreAiEngine
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class FitCoreAiEngineTest {

    @Test
    fun offlineAdviceIncludesConcreteNextAction() {
        val user = UserEntity(
            name = "Yahir",
            weightKg = 76f,
            heightCm = 172f,
            age = 24,
            goal = "Ganar músculo",
            activityLevel = "Moderado",
            targetCalories = 2600,
            targetProteinG = 150
        )

        val advice = FitCoreAiEngine.generateOfflineAdvice(
            user = user,
            logs = listOf(HealthLogEntity(waterIntakeMl = 900, sleepDurationHours = 6f)),
            requestType = "nutrición y hábitos"
        )

        assertTrue(advice.contains("FitCore AI"))
        assertTrue(advice.contains("Acción:"))
        assertTrue(advice.contains("Yahir"))
    }

    @Test
    fun coachPromptKeepsMedicalSafetyRule() {
        val prompt = FitCoreAiEngine.buildCoachPrompt(
            user = UserEntity(name = "Yahir"),
            recentVitals = emptyList(),
            requestType = "plan de entrenamiento"
        )

        assertTrue(prompt.contains("sin diagnósticos médicos"))
        assertTrue(prompt.contains("acción concreta"))
    }
}

