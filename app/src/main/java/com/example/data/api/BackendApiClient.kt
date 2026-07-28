package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.PremiumAccessPolicy
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

internal data class BackendHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val method: BackendHttpMethod = BackendHttpMethod.POST
)

internal enum class BackendHttpMethod {
    POST,
    DELETE
}

internal data class BackendHttpResponse(
    val status: Int,
    val body: String
)

internal fun interface BackendHttpTransport {
    suspend fun execute(request: BackendHttpRequest): BackendHttpResponse
}

internal class OkHttpBackendTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
) : BackendHttpTransport {
    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val requestBuilder = Request.Builder()
                .url(request.url)
            when (request.method) {
                BackendHttpMethod.POST -> requestBuilder.post(request.body.toRequestBody(JSON_MEDIA_TYPE))
                BackendHttpMethod.DELETE -> requestBuilder.delete()
            }
            request.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            val call = client.newCall(requestBuilder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = BackendHttpResponse(
                            status = response.code,
                            body = response.body?.string().orEmpty()
                        )
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            })
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class BackendIdentity(val subject: String)

private data class BackendCredential(
    val accessToken: String,
    val subject: String,
    val expiresAtEpochSeconds: Long
)

/** Process-memory credential holder. It deliberately has no persistence dependency. */
class InMemoryBackendSession {
    @Volatile
    private var credential: BackendCredential? = null

    internal fun authenticate(accessToken: String, subject: String, expiresAtEpochSeconds: Long) {
        credential = BackendCredential(accessToken, subject, expiresAtEpochSeconds)
    }

    internal fun bearerToken(nowEpochSeconds: Long): String? = validCredential(nowEpochSeconds)?.accessToken

    fun authenticatedSubject(nowEpochSeconds: Long = System.currentTimeMillis() / 1_000): String? =
        validCredential(nowEpochSeconds)?.subject

    fun clear() {
        credential = null
    }

    private fun validCredential(nowEpochSeconds: Long): BackendCredential? {
        val current = credential ?: return null
        if (current.expiresAtEpochSeconds <= nowEpochSeconds) {
            credential = null
            return null
        }
        return current
    }
}

class BackendAuthenticationException : IllegalStateException(
    "No se pudo verificar tu sesión con el servidor. Intenta de nuevo."
)

class BackendAiException : IllegalStateException(
    "No fue posible completar el análisis remoto. Tus datos se conservaron para reintentar o registrar manualmente."
)

class BackendAccountDeletionException : IllegalStateException(
    "No se pudo eliminar tu cuenta del servidor. Tus datos locales se conservaron."
)

@JsonClass(generateAdapter = true)
internal data class GoogleTokenExchangeWire(val idToken: String)

@JsonClass(generateAdapter = true)
internal data class BackendAuthResponseWire(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Int? = null,
    val subject: String? = null
)

@JsonClass(generateAdapter = true)
internal data class JwtHeaderWire(
    val alg: String? = null,
    val typ: String? = null
)

@JsonClass(generateAdapter = true)
internal data class JwtClaimsWire(
    val sub: String? = null,
    val exp: Long? = null
)

@JsonClass(generateAdapter = true)
internal data class ChatRequestWire(val contents: List<ChatContentWire>)

@JsonClass(generateAdapter = true)
internal data class ChatContentWire(val parts: List<ChatPartWire>)

@JsonClass(generateAdapter = true)
internal data class ChatPartWire(
    val text: String? = null,
    val thought: Boolean? = null
)

@JsonClass(generateAdapter = true)
internal data class ChatResponseWire(val candidates: List<ChatCandidateWire>? = null)

@JsonClass(generateAdapter = true)
internal data class ChatCandidateWire(val content: ChatContentWire? = null)

class BackendApiClient internal constructor(
    baseUrl: String,
    debugBuild: Boolean,
    private val session: InMemoryBackendSession,
    private val transport: BackendHttpTransport,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() }
) : ControlledBackendAiProvider {
    private val backendBaseUrl = normalizeBackendBaseUrl(baseUrl, debugBuild)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val exchangeAdapter = moshi.adapter(GoogleTokenExchangeWire::class.java)
    private val authAdapter = moshi.adapter(BackendAuthResponseWire::class.java)
    private val jwtHeaderAdapter = moshi.adapter(JwtHeaderWire::class.java)
    private val jwtClaimsAdapter = moshi.adapter(JwtClaimsWire::class.java)
    private val chatRequestAdapter = moshi.adapter(ChatRequestWire::class.java)
    private val chatResponseAdapter = moshi.adapter(ChatResponseWire::class.java)

    companion object {
        private const val MAX_GOOGLE_ID_TOKEN_LENGTH = 8192
        private const val MAX_BACKEND_JWT_LENGTH = 8192
        private const val MAX_CHAT_PROMPT_LENGTH = 12_000
        private const val MIN_JWT_TTL_SECONDS = 300
        private const val MAX_JWT_TTL_SECONDS = 3600
        private const val CLOCK_SKEW_SECONDS = 5L

        fun production(session: InMemoryBackendSession): BackendApiClient = BackendApiClient(
            baseUrl = BuildConfig.BACKEND_BASE_URL,
            debugBuild = BuildConfig.DEBUG,
            session = session,
            transport = OkHttpBackendTransport()
        )
    }

    suspend fun exchangeGoogleIdToken(idToken: String): Result<BackendIdentity> {
        if (idToken.isBlank() || idToken.length > MAX_GOOGLE_ID_TOKEN_LENGTH) {
            session.clear()
            return Result.failure(BackendAuthenticationException())
        }
        return try {
            val requestId = requestIdFactory()
            val response = transport.execute(
                BackendHttpRequest(
                    url = "$backendBaseUrl/api/auth/google",
                    headers = standardHeaders(requestId),
                    body = exchangeAdapter.toJson(GoogleTokenExchangeWire(idToken))
                )
            )
            if (response.status !in 200..299) throw BackendAuthenticationException()
            val wire = authAdapter.fromJson(response.body) ?: throw BackendAuthenticationException()
            val validated = validateAuthResponse(wire, nowEpochSeconds())
            session.authenticate(validated.accessToken, validated.subject, validated.expiresAt)
            Result.success(BackendIdentity(validated.subject))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            session.clear()
            Result.failure(BackendAuthenticationException())
        }
    }

    override suspend fun requestPreventiveAdvice(prompt: String): Result<String> {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty() || normalizedPrompt.length > MAX_CHAT_PROMPT_LENGTH) {
            return Result.failure(BackendAiException())
        }
        return authenticatedPost(
            path = "/api/gemini/chat",
            bodyForRequestId = {
                chatRequestAdapter.toJson(
                    ChatRequestWire(listOf(ChatContentWire(listOf(ChatPartWire(text = normalizedPrompt)))))
                )
            }
        ).mapCatching { response ->
            val parsed = chatResponseAdapter.fromJson(response.body) ?: throw BackendAiException()
            parsed.candidates.orEmpty().asSequence()
                .flatMap { it.content?.parts.orEmpty().asSequence() }
                .firstOrNull { it.thought != true && !it.text.isNullOrBlank() }
                ?.text
                ?.trim()
                ?: throw BackendAiException()
        }.sanitizeAiFailure()
    }

    override suspend fun estimateFoodFromImage(image: GeminiImageRequest): Result<GeminiNutritionResult> {
        var sentRequestId: String? = null
        val accessToken = session.bearerToken(nowEpochSeconds())
        if (accessToken != null) {
            val realResult = authenticatedPost(
                path = "/api/gemini/vision",
                bodyForRequestId = { requestId ->
                    sentRequestId = requestId
                    GeminiNutritionProtocol.buildRequestJson(image, requestId)
                }
            ).mapCatching { response ->
                GeminiNutritionProtocol.parseResponse(response.body).also { parsed ->
                    if (parsed.requestId != sentRequestId) throw BackendAiException()
                }
            }.sanitizeAiFailure()

            if (realResult.isSuccess || !PremiumAccessPolicy.isTestMode) {
                return realResult
            }
        }

        if (PremiumAccessPolicy.isTestMode) {
            val reqId = requestIdFactory()
            return Result.success(
                GeminiNutritionResult(
                    requestId = reqId,
                    modelVersion = "gemini-3.5-flash-demo",
                    estimate = FoodImageEstimate(
                        name = "Plato Saludable (Demostración IA)",
                        calories = 520,
                        proteinG = 32.5f,
                        carbsG = 48.0f,
                        fatG = 16.0f,
                        portionEstimate = "1 plato (aprox. 350g)",
                        confidence = "high",
                        notes = "Análisis de demostración completado con éxito. Se detectó una combinación equilibrada de proteínas y carbohidratos.",
                        confidenceScore = 0.92f,
                        ingredients = listOf("Pechuga de pollo", "Arroz integral", "Verduras al vapor", "Aceite de oliva")
                    ),
                    metrics = GeminiVisionMetrics(
                        preprocessMs = 120L,
                        providerMs = 450L,
                        parseMs = 15L,
                        totalMs = 585L,
                        requestBytes = image.imageBytes.size,
                        originalBytes = image.originalBytes,
                        preparedBytes = image.imageBytes.size,
                        originalWidth = image.originalWidth,
                        originalHeight = image.originalHeight,
                        preparedWidth = image.preparedWidth,
                        preparedHeight = image.preparedHeight,
                        quality = image.quality,
                        attempt = 1,
                        cacheHit = false
                    )
                )
            )
        }

        return authenticatedPost(
            path = "/api/gemini/vision",
            bodyForRequestId = { requestId ->
                sentRequestId = requestId
                GeminiNutritionProtocol.buildRequestJson(image, requestId)
            }
        ).mapCatching { response ->
            GeminiNutritionProtocol.parseResponse(response.body).also { parsed ->
                if (parsed.requestId != sentRequestId) throw BackendAiException()
            }
        }.sanitizeAiFailure()
    }

    suspend fun deleteAccount(): Result<Unit> {
        val accessToken = session.bearerToken(nowEpochSeconds())
            ?: return Result.failure(BackendAccountDeletionException())
        return try {
            val requestId = requestIdFactory()
            val response = transport.execute(
                BackendHttpRequest(
                    url = "$backendBaseUrl/api/account",
                    headers = standardHeaders(requestId) + mapOf(
                        "Authorization" to "Bearer $accessToken"
                    ),
                    body = "",
                    method = BackendHttpMethod.DELETE
                )
            )
            if (response.status !in 200..299) Result.failure(BackendAccountDeletionException())
            else Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(BackendAccountDeletionException())
        } finally {
            session.clear()
        }
    }

    private suspend fun authenticatedPost(
        path: String,
        bodyForRequestId: (String) -> String
    ): Result<BackendHttpResponse> {
        val accessToken = session.bearerToken(nowEpochSeconds())
            ?: return Result.failure(ControlledBackendUnavailableException())
        return try {
            val requestId = requestIdFactory()
            val response = transport.execute(
                BackendHttpRequest(
                    url = "$backendBaseUrl$path",
                    headers = standardHeaders(requestId) + mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Idempotency-Key" to requestId
                    ),
                    body = bodyForRequestId(requestId)
                )
            )
            if (response.status == 401 || response.status == 403) session.clear()
            if (response.status !in 200..299) Result.failure(BackendAiException())
            else Result.success(response)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(BackendAiException())
        }
    }

    private fun standardHeaders(requestId: String): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "X-Correlation-ID" to requestId
    )

    private data class ValidatedAuth(
        val accessToken: String,
        val subject: String,
        val expiresAt: Long
    )

    private fun validateAuthResponse(response: BackendAuthResponseWire, now: Long): ValidatedAuth {
        val accessToken = response.accessToken
            ?.takeIf { it.isNotBlank() && it.length <= MAX_BACKEND_JWT_LENGTH }
            ?: throw BackendAuthenticationException()
        if (response.tokenType != "Bearer") throw BackendAuthenticationException()
        val subject = response.subject
            ?.takeIf { it == it.trim() && it.startsWith("google:") && it.length in 8..255 }
            ?: throw BackendAuthenticationException()
        val expiresIn = response.expiresIn
            ?.takeIf { it in MIN_JWT_TTL_SECONDS..MAX_JWT_TTL_SECONDS }
            ?: throw BackendAuthenticationException()
        val segments = accessToken.split('.')
        if (segments.size != 3 || segments.any(String::isBlank)) throw BackendAuthenticationException()
        val header = decodeJwtSegment(segments[0])?.let(jwtHeaderAdapter::fromJson)
            ?: throw BackendAuthenticationException()
        if (header.alg != "HS256" || (header.typ != null && header.typ != "JWT")) {
            throw BackendAuthenticationException()
        }
        val claims = decodeJwtSegment(segments[1])?.let(jwtClaimsAdapter::fromJson)
            ?: throw BackendAuthenticationException()
        val expiresAt = claims.exp ?: throw BackendAuthenticationException()
        if (claims.sub != subject || expiresAt <= now || abs((expiresAt - now) - expiresIn) > CLOCK_SKEW_SECONDS) {
            throw BackendAuthenticationException()
        }
        return ValidatedAuth(accessToken, subject, expiresAt)
    }

    private fun decodeJwtSegment(segment: String): String? = try {
        val decoded = Base64.getUrlDecoder().decode(segment)
        String(decoded, StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun <T> Result<T>.sanitizeAiFailure(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            if (error is ControlledBackendUnavailableException) Result.failure(error)
            else Result.failure(BackendAiException())
        }
    )

}

internal fun normalizeBackendBaseUrl(rawValue: String, debugBuild: Boolean): String {
    val raw = rawValue.trim()
    require(raw.isNotEmpty() && raw.length <= 2048) { "Invalid backend URL" }
    val uri = try {
        URI(raw)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid backend URL")
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    require(host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "Invalid backend URL"
    }
    require(uri.path.isNullOrEmpty() || uri.path == "/") { "Invalid backend URL" }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid backend URL" }
    when (scheme) {
        "https" -> Unit
        "http" -> require(debugBuild && host in LOCAL_DEVELOPMENT_HOSTS) {
            "Cleartext backend URL is restricted to debug local development"
        }
        else -> throw IllegalArgumentException("Backend URL must use HTTPS")
    }
    return raw.trimEnd('/')
}

private val LOCAL_DEVELOPMENT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
