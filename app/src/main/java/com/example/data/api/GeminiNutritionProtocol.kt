package com.example.data.api

import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.roundToInt

data class GeminiImageRequest(
    val imageBytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val originalBytes: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val preparedWidth: Int,
    val preparedHeight: Int,
    val quality: Int
)

data class GeminiVisionMetrics(
    val preprocessMs: Long,
    val providerMs: Long,
    val parseMs: Long,
    val totalMs: Long,
    val requestBytes: Int,
    val originalBytes: Int,
    val preparedBytes: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val preparedWidth: Int,
    val preparedHeight: Int,
    val quality: Int,
    val attempt: Int,
    val cacheHit: Boolean
)

data class GeminiNutritionResult(
    val requestId: String,
    val modelVersion: String,
    val estimate: FoodImageEstimate,
    val metrics: GeminiVisionMetrics
)

class GeminiProtocolException(
    val code: String,
    val field: String? = null,
    val userMessage: String = "La respuesta de Gemini no se pudo validar. Revisa los datos o intenta de nuevo."
) : IllegalArgumentException(code)

@JsonClass(generateAdapter = true)
internal data class VisionRequestWire(
    val requestId: String,
    val imageBase64: String,
    val mimeType: String,
    val originalBytes: Int,
    val preparedBytes: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val preparedWidth: Int,
    val preparedHeight: Int,
    val quality: Int
)

@JsonClass(generateAdapter = true)
internal data class VisionResponseWire(
    val requestId: String? = null,
    val modelVersion: String? = null,
    val nutrition: NutritionWire? = null,
    val metrics: MetricsWire? = null
)

@JsonClass(generateAdapter = true)
internal data class NutritionWire(
    val foodName: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val portionEstimate: String? = null,
    val confidence: Double? = null,
    val notes: String? = null,
    val ingredients: List<String>? = null
)

@JsonClass(generateAdapter = true)
internal data class MetricsWire(
    val preprocessMs: Long? = null,
    val providerMs: Long? = null,
    val parseMs: Long? = null,
    val totalMs: Long? = null,
    val requestBytes: Int? = null,
    val originalBytes: Int? = null,
    val preparedBytes: Int? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val preparedWidth: Int? = null,
    val preparedHeight: Int? = null,
    val quality: Int? = null,
    val attempt: Int? = null,
    val cacheHit: Boolean? = null
)

object GeminiNutritionProtocol {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(VisionRequestWire::class.java)
    private val responseAdapter = moshi.adapter(VisionResponseWire::class.java)

    fun buildRequestJson(request: GeminiImageRequest, requestId: String): String {
        if (request.imageBytes.isEmpty()) throw invalid("INVALID_IMAGE", "imageBytes")
        requirePositive(request.originalBytes, "originalBytes")
        requirePositive(request.originalWidth, "originalWidth")
        requirePositive(request.originalHeight, "originalHeight")
        requirePositive(request.preparedWidth, "preparedWidth")
        requirePositive(request.preparedHeight, "preparedHeight")
        if (request.quality !in 1..100) throw invalid("INVALID_QUALITY", "quality")
        if (request.mimeType != "image/jpeg") throw invalid("UNSUPPORTED_IMAGE_TYPE", "mimeType")
        val wire = VisionRequestWire(
            requestId = requestId,
            imageBase64 = Base64.encodeToString(request.imageBytes, Base64.NO_WRAP),
            mimeType = request.mimeType,
            originalBytes = request.originalBytes,
            preparedBytes = request.imageBytes.size,
            originalWidth = request.originalWidth,
            originalHeight = request.originalHeight,
            preparedWidth = request.preparedWidth,
            preparedHeight = request.preparedHeight,
            quality = request.quality
        )
        return requestAdapter.toJson(wire)
    }

    fun parseResponse(raw: String): GeminiNutritionResult {
        val wire = try {
            responseAdapter.fromJson(raw)
        } catch (_: Exception) {
            throw invalid("INVALID_RESPONSE_JSON")
        } ?: throw invalid("INVALID_RESPONSE_JSON")

        val nutrition = wire.nutrition ?: throw invalid("MISSING_NUTRITION", "nutrition")
        val metrics = wire.metrics ?: throw invalid("MISSING_METRICS", "metrics")
        val requestId = requireText(wire.requestId, "requestId")
        val modelVersion = requireText(wire.modelVersion, "modelVersion")
        val calories = requireNonNegative(nutrition.calories, "calories")
        if (calories > Int.MAX_VALUE) throw invalid("INVALID_NON_NEGATIVE_NUMBER", "calories")
        val confidence = nutrition.confidence
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: throw invalid("INVALID_CONFIDENCE", "confidence")
        val ingredients = nutrition.ingredients
            ?: throw invalid("INVALID_LIST", "ingredients")
        val normalizedIngredients = ingredients.mapIndexed { index, ingredient ->
            requireText(ingredient, "ingredients[$index]")
        }
        val confidenceScore = confidence.toFloat()

        return GeminiNutritionResult(
            requestId = requestId,
            modelVersion = modelVersion,
            estimate = FoodImageEstimate(
                name = requireText(nutrition.foodName, "foodName"),
                calories = calories.roundToInt(),
                proteinG = requireNonNegative(nutrition.protein, "protein").toFloat(),
                carbsG = requireNonNegative(nutrition.carbs, "carbs").toFloat(),
                fatG = requireNonNegative(nutrition.fat, "fat").toFloat(),
                portionEstimate = requireText(nutrition.portionEstimate, "portionEstimate"),
                confidence = confidenceLabel(confidenceScore),
                notes = nutrition.notes ?: throw invalid("MISSING_FIELD", "notes"),
                confidenceScore = confidenceScore,
                ingredients = normalizedIngredients
            ),
            metrics = GeminiVisionMetrics(
                preprocessMs = requireNonNegative(metrics.preprocessMs, "preprocessMs"),
                providerMs = requireNonNegative(metrics.providerMs, "providerMs"),
                parseMs = requireNonNegative(metrics.parseMs, "parseMs"),
                totalMs = requireNonNegative(metrics.totalMs, "totalMs"),
                requestBytes = requireNonNegative(metrics.requestBytes, "requestBytes"),
                originalBytes = requireNonNegative(metrics.originalBytes, "originalBytes"),
                preparedBytes = requireNonNegative(metrics.preparedBytes, "preparedBytes"),
                originalWidth = requirePositive(metrics.originalWidth, "originalWidth"),
                originalHeight = requirePositive(metrics.originalHeight, "originalHeight"),
                preparedWidth = requirePositive(metrics.preparedWidth, "preparedWidth"),
                preparedHeight = requirePositive(metrics.preparedHeight, "preparedHeight"),
                quality = requirePositive(metrics.quality, "quality").also {
                    if (it > 100) throw invalid("INVALID_QUALITY", "quality")
                },
                attempt = requirePositive(metrics.attempt, "attempt"),
                cacheHit = metrics.cacheHit ?: throw invalid("MISSING_FIELD", "cacheHit")
            )
        )
    }

    private fun confidenceLabel(value: Float): String = when {
        value >= 0.75f -> "high"
        value >= 0.45f -> "medium"
        else -> "low"
    }

    private fun requireText(value: String?, field: String): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: throw invalid("INVALID_TEXT", field)

    private fun requireNonNegative(value: Double?, field: String): Double =
        value?.takeIf { it.isFinite() && it >= 0.0 }
            ?: throw invalid(if (value == null) "MISSING_FIELD" else "INVALID_NON_NEGATIVE_NUMBER", field)

    private fun requireNonNegative(value: Long?, field: String): Long =
        value?.takeIf { it >= 0L }
            ?: throw invalid(if (value == null) "MISSING_FIELD" else "INVALID_NON_NEGATIVE_NUMBER", field)

    private fun requireNonNegative(value: Int?, field: String): Int =
        value?.takeIf { it >= 0 }
            ?: throw invalid(if (value == null) "MISSING_FIELD" else "INVALID_NON_NEGATIVE_NUMBER", field)

    private fun requirePositive(value: Int?, field: String): Int =
        value?.takeIf { it > 0 }
            ?: throw invalid(if (value == null) "MISSING_FIELD" else "INVALID_POSITIVE_NUMBER", field)

    private fun invalid(code: String, field: String? = null) = GeminiProtocolException(code, field)
}
