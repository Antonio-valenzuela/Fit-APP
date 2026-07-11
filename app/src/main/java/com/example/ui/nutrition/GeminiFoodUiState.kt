package com.example.ui.nutrition

import com.example.data.api.FoodImageEstimate
import com.example.data.api.GeminiImageRequest

enum class GeminiFoodPhase {
    IDLE,
    AWAITING_CONSENT,
    LOADING,
    REVIEW,
    ERROR,
    CANCELLED
}

data class GeminiFoodUiState(
    val phase: GeminiFoodPhase = GeminiFoodPhase.IDLE,
    val request: GeminiImageRequest? = null,
    val estimate: FoodImageEstimate? = null,
    val description: String = "",
    val message: String? = null,
    val saveRequested: Boolean = false
) {
    val isLoading: Boolean get() = phase == GeminiFoodPhase.LOADING
    val canRetry: Boolean get() = request != null && !isLoading
}

object GeminiFoodStateReducer {
    fun prepared(state: GeminiFoodUiState, request: GeminiImageRequest): GeminiFoodUiState = state.copy(
        phase = GeminiFoodPhase.AWAITING_CONSENT,
        request = request,
        estimate = null,
        message = null,
        saveRequested = false
    )

    fun start(state: GeminiFoodUiState): GeminiFoodUiState {
        if (state.isLoading || state.request == null) return state
        return state.copy(
            phase = GeminiFoodPhase.LOADING,
            estimate = null,
            message = "Analizando comida con Gemini...",
            saveRequested = false
        )
    }

    fun success(state: GeminiFoodUiState, estimate: FoodImageEstimate): GeminiFoodUiState = state.copy(
        phase = GeminiFoodPhase.REVIEW,
        estimate = estimate,
        message = if (estimate.name.equals("Comida no identificada", ignoreCase = true) || estimate.confidenceScore < 0.45f) {
            "No pudimos identificar bien la comida. Puedes editar o registrar manualmente."
        } else {
            "Comida detectada: ${estimate.name} - ${estimate.calories} kcal. Revisa los campos y toca Guardar."
        },
        saveRequested = false
    )

    fun failure(state: GeminiFoodUiState, message: String): GeminiFoodUiState = state.copy(
        phase = GeminiFoodPhase.ERROR,
        estimate = null,
        message = message,
        saveRequested = false
    )

    fun cancel(state: GeminiFoodUiState): GeminiFoodUiState = state.copy(
        phase = GeminiFoodPhase.CANCELLED,
        estimate = null,
        message = "Analisis cancelado. Conservamos la imagen y los datos para reintentar o registrar manualmente.",
        saveRequested = false
    )

    fun updateDescription(state: GeminiFoodUiState, description: String): GeminiFoodUiState =
        state.copy(description = description)

    fun resetForManual(state: GeminiFoodUiState): GeminiFoodUiState = GeminiFoodUiState(
        description = state.description,
        message = "Registro manual activo. Puedes escribir los datos."
    )
}
