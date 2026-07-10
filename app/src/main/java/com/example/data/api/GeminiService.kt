package com.example.data.api

import android.util.Log
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

data class FoodImageEstimate(
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val portionEstimate: String,
    val confidence: String,
    val notes: String
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
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

    suspend fun estimateFoodFromImage(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Result<FoodImageEstimate> = withContext(Dispatchers.IO) {
        val backendKey = BuildConfig.BACKEND_API_KEY
        if (backendKey.isEmpty() || backendKey == "YOUR_BACKEND_API_KEY_HERE" || backendKey.contains("placeholder", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalStateException("Configura BACKEND_API_KEY para analizar fotos de comida.")
            )
        }

        val prompt = """
            Analiza esta comida para un registro personal de nutricion.
            Estima calorias y macronutrientes de forma aproximada.
            Devuelve solo JSON valido, sin markdown:
            {
              "foodName": "string",
              "calories": 0,
              "protein": 0.0,
              "carbs": 0.0,
              "fat": 0.0,
              "portionEstimate": "string",
              "confidence": "high|medium|low",
              "notes": "string"
            }
            Reglas:
            - Las calorias y macros deben ser aproximados.
            - Si hay varios alimentos, resumir como una comida.
            - Si no reconoces comida, foodName debe ser "Comida no identificada", confidence "low" y valores 0.
            - No incluyas markdown.
            - No incluyas texto fuera del JSON.
            Las cifras son estimaciones, no mediciones medicas.
        """.trimIndent()

        val imageB64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        Log.d("GeminiService", "Food image request: ${imageBytes.size} bytes, mime=$mimeType")
        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", imageB64)
                            })
                        })
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        try {
            val request = Request.Builder()
                .url(backendUrlVision)
                .addHeader("Authorization", "Bearer $backendKey")
                .post(jsonRequest.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        503 -> "El servidor de IA de Gemini está temporalmente sobrecargado (Error 503). Por favor, introduce los datos manualmente o intenta en unos segundos."
                        504, 502 -> "Tiempo de espera agotado en la red (Error ${response.code}). Por favor, intenta de nuevo."
                        429 -> "Se ha superado el límite de solicitudes de IA (Error 429). Inténtalo más tarde."
                        else -> "No se pudo conectar con la IA de Gemini (Código de error: ${response.code})."
                    }
                    return@withContext Result.failure(IllegalStateException(errorMsg))
                }
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IllegalStateException("Respuesta vacia de Gemini."))
                val text = JSONObject(responseBody)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val parsed = JSONObject(extractJsonObject(text))
                if (BuildConfig.DEBUG) {
                    Log.d("GeminiService", "Food image parsed JSON: ${parsed.toString()}")
                }
                val confidence = parsed.optString("confidence", "low").lowercase()
                    .takeIf { it in setOf("high", "medium", "low") } ?: "low"
                val notes = parsed.optString("notes")
                    .ifBlank { parsed.optString("confidenceNote", "Estimacion visual aproximada.") }
                Result.success(
                    FoodImageEstimate(
                        name = parsed.optString("foodName")
                            .ifBlank { parsed.optString("name", "Comida por foto") }
                            .ifBlank { "Comida por foto" },
                        calories = parsed.optInt("calories", 0).coerceAtLeast(0),
                        proteinG = parsed.optDouble("protein", parsed.optDouble("proteinG", 0.0)).toFloat().coerceAtLeast(0f),
                        carbsG = parsed.optDouble("carbs", parsed.optDouble("carbsG", 0.0)).toFloat().coerceAtLeast(0f),
                        fatG = parsed.optDouble("fat", parsed.optDouble("fatG", 0.0)).toFloat().coerceAtLeast(0f),
                        portionEstimate = parsed.optString("portionEstimate", "Porcion visual aproximada."),
                        confidence = confidence,
                        notes = notes.ifBlank { "Estimacion visual aproximada." }
                    )
                )
            }
        } catch (error: InterruptedIOException) {
            Log.e("GeminiService", "Food image estimate timeout: ${error.message}", error)
            Result.failure(IllegalStateException("Gemini tardo demasiado en analizar la foto. Intenta de nuevo con buena luz o una foto mas clara."))
        } catch (error: Exception) {
            // Re-throw CancellationException to respect structured concurrency
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e("GeminiService", "Food image estimate error: ${error.message}", error)
            Result.failure(IllegalStateException("No se pudo analizar la comida. Revisa tu conexión o intenta de nuevo."))
        }
    }

    private fun extractJsonObject(text: String): String {
        val cleaned = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) return cleaned
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1)
        throw IllegalArgumentException("La IA no devolvio JSON valido.")
    }
}
