package com.example.data.api

import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.CancellationException

data class FoodImageEstimate(
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val portionEstimate: String,
    val confidence: String,
    val notes: String,
    val confidenceScore: Float = 0f,
    val ingredients: List<String> = emptyList()
)

/**
 * Boundary for AI operations that may only be implemented by calling a controlled backend.
 * Client-side provider credentials are intentionally not part of this contract.
 */
interface ControlledBackendAiProvider {
    suspend fun requestPreventiveAdvice(prompt: String): Result<String>

    suspend fun estimateFoodFromImage(image: GeminiImageRequest): Result<GeminiNutritionResult>
}

class ControlledBackendUnavailableException : IllegalStateException(
    "El análisis remoto está desactivado hasta disponer de autenticación segura en el backend. " +
        "Tus datos se conservaron para reintentar o registrar manualmente."
)

class GeminiService(
    private val remoteProvider: ControlledBackendAiProvider
) {

    suspend fun getPreventiveAdvice(
        user: UserEntity,
        recentVitals: List<HealthLogEntity>,
        requestType: String
    ): String {
        val prompt = FitCoreAiEngine.buildCoachPrompt(user, recentVitals, requestType)
        val remoteResult = try {
            remoteProvider.requestPreventiveAdvice(prompt)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        val remoteAdvice = remoteResult?.getOrNull()?.trim().orEmpty()
        return remoteAdvice.ifBlank {
            FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)
        }
    }

    suspend fun estimateFoodFromImage(image: GeminiImageRequest): Result<GeminiNutritionResult> {
        return try {
            remoteProvider.estimateFoodFromImage(image)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(IllegalStateException("No se pudo analizar la comida. Tus datos se conservaron para reintentar o registrar manualmente."))
        }
    }
}
