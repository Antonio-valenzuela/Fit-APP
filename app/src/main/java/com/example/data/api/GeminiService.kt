package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")

    init {
        if (!BuildConfig.DEBUG && !baseUrl.startsWith("https://", ignoreCase = true)) {
            throw IllegalStateException("HTTPS is mandatory in production for API Base URL: $baseUrl")
        }
    }

    private val backendUrlChat = "$baseUrl/api/gemini/chat"
    private val backendUrlVision = "$baseUrl/api/gemini/vision"

    suspend fun getPreventiveAdvice(
        user: UserEntity,
        recentVitals: List<HealthLogEntity>,
        requestType: String
    ): String = withContext(Dispatchers.IO) {
        val backendKey = BuildConfig.BACKEND_API_KEY
        if (backendKey.isEmpty() || backendKey == "YOUR_BACKEND_API_KEY_HERE" || backendKey.contains("placeholder", ignoreCase = true)) {
            Log.w("GeminiService", "Backend API key no configurada. Usando FitCore AI local.")
            return@withContext FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)
        }

        val prompt = FitCoreAiEngine.buildCoachPrompt(user, recentVitals, requestType)
        val jsonRequest = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        try {
            val request = Request.Builder()
                .url(backendUrlChat)
                .addHeader("Authorization", "Bearer $backendKey")
                .post(jsonRequest.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiService", "Proxy API response failed: ${response.code}")
                    return@withContext FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)
                }

                val responseBody = response.body?.string()
                    ?: return@withContext FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)

                runCatching {
                    JSONObject(responseBody)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                }.getOrElse {
                    Log.e("GeminiService", "Proxy response parse error: ${it.message}", it)
                    FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)
                }
            }
        } catch (e: Exception) {
            // Re-throw CancellationException to respect structured concurrency
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("GeminiService", "Proxy API call error: ${e.message}", e)
            FitCoreAiEngine.generateOfflineAdvice(user, recentVitals, requestType)
        }
    }

    suspend fun estimateFoodFromImage(image: GeminiImageRequest): Result<GeminiNutritionResult> = withContext(Dispatchers.IO) {
        val backendKey = BuildConfig.BACKEND_API_KEY
        if (backendKey.isEmpty() || backendKey == "YOUR_BACKEND_API_KEY_HERE" || backendKey.contains("placeholder", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalStateException("Configura BACKEND_API_KEY para analizar fotos de comida.")
            )
        }

        val requestId = UUID.randomUUID().toString()
        val jsonRequest = GeminiNutritionProtocol.buildRequestJson(image, requestId)
        Log.i(
            "GeminiMetrics",
            "event=request_start requestId=$requestId originalBytes=${image.originalBytes} preparedBytes=${image.imageBytes.size} " +
                "original=${image.originalWidth}x${image.originalHeight} prepared=${image.preparedWidth}x${image.preparedHeight} quality=${image.quality}"
        )

        try {
            val httpRequest = Request.Builder()
                .url(backendUrlVision)
                .addHeader("Authorization", "Bearer $backendKey")
                .post(jsonRequest.toRequestBody(mediaType))
                .build()

            client.newCall(httpRequest).awaitResponse().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(friendlyBackendError(response.code, backendErrorCode(responseBody)))
                    )
                }
                val body = responseBody
                    ?: return@withContext Result.failure(IllegalStateException("Gemini devolvio una respuesta vacia. Tus datos se conservaron."))
                val parsed = GeminiNutritionProtocol.parseResponse(body)
                Log.i(
                    "GeminiMetrics",
                    "event=request_complete requestId=${parsed.requestId} model=${parsed.modelVersion} " +
                        "preprocessMs=${parsed.metrics.preprocessMs} providerMs=${parsed.metrics.providerMs} " +
                        "parseMs=${parsed.metrics.parseMs} totalMs=${parsed.metrics.totalMs} " +
                        "requestBytes=${parsed.metrics.requestBytes} attempt=${parsed.metrics.attempt} cacheHit=${parsed.metrics.cacheHit}"
                )
                Result.success(parsed)
            }
        } catch (error: GeminiProtocolException) {
            Log.w("GeminiService", "Food image response rejected: code=${error.code} field=${error.field}")
            Result.failure(IllegalStateException(error.userMessage))
        } catch (error: IOException) {
            Log.w("GeminiService", "Food image network failure: ${error.javaClass.simpleName}")
            Result.failure(IllegalStateException("El analisis tardo demasiado o se interrumpio. Tus datos se conservaron para reintentar."))
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e("GeminiService", "Food image estimate failure: ${error.javaClass.simpleName}")
            Result.failure(IllegalStateException("No se pudo analizar la comida. Tus datos se conservaron para reintentar o registrar manualmente."))
        }
    }

    private fun backendErrorCode(body: String?): String? = runCatching {
        body?.let { JSONObject(it).optString("code").takeIf(String::isNotBlank) }
    }.getOrNull()

    private fun friendlyBackendError(status: Int, code: String?): String = when (code) {
        "PROVIDER_UNAVAILABLE", "CIRCUIT_OPEN" ->
            "Gemini esta temporalmente ocupado. Tus datos se conservaron; reintenta o usa el registro manual/local."
        "PROVIDER_TIMEOUT" ->
            "El analisis tardo demasiado. Tus datos se conservaron para reintentar o registrar manualmente."
        "PROVIDER_RATE_LIMIT" ->
            "Se alcanzo el limite temporal de analisis. Tus datos se conservaron; intenta mas tarde."
        "CONCURRENT_REQUEST" ->
            "Ya hay un analisis en curso. Espera el resultado o cancelalo antes de iniciar otro."
        "INVALID_NUTRITION_RESULT" ->
            "Gemini devolvio un resultado invalido. Revisa los datos o registra la comida manualmente."
        else -> when (status) {
            413 -> "La imagen es demasiado grande. Usa una foto mas pequena."
            429 -> "Se alcanzo el limite temporal de analisis. Intenta mas tarde."
            503 -> "Gemini esta temporalmente ocupado. Tus datos se conservaron para reintentar."
            504 -> "El analisis tardo demasiado. Tus datos se conservaron para reintentar."
            else -> "No fue posible completar el analisis. Tus datos se conservaron."
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}
