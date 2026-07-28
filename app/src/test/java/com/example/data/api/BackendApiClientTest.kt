package com.example.data.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackendApiClientTest {
    private val now = 1_750_000_000L

    @Test
    fun exchangesOnlyGoogleIdTokenAndStoresValidatedJwtInMemory() = runTest {
        val subject = "google:opaque-subject"
        val transport = RecordingTransport(
            BackendHttpResponse(200, authResponse(subject, now + 900, 900))
        )
        val session = InMemoryBackendSession()
        val client = client(transport, session)

        val identity = client.exchangeGoogleIdToken("google-id-token").getOrThrow()

        assertEquals(subject, identity.subject)
        assertEquals("https://api.fitplan.mx/api/auth/google", transport.requests.single().url)
        assertEquals("{\"idToken\":\"google-id-token\"}", transport.requests.single().body)
        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
        assertEquals(subject, session.authenticatedSubject(now))
        assertTrue(session.bearerToken(now).orEmpty().startsWith("eyJ"))
    }

    @Test
    fun rejectsMismatchedOrExpiredJwtWithoutAuthenticatingSession() = runTest {
        val session = InMemoryBackendSession()
        val mismatch = RecordingTransport(
            BackendHttpResponse(200, authResponse("google:server-subject", now + 900, 900, "google:other"))
        )

        assertTrue(client(mismatch, session).exchangeGoogleIdToken("id-token").isFailure)
        assertNull(session.bearerToken(now))

        val expired = RecordingTransport(
            BackendHttpResponse(200, authResponse("google:server-subject", now - 1, 900))
        )
        assertTrue(client(expired, session).exchangeGoogleIdToken("id-token").isFailure)
        assertNull(session.authenticatedSubject(now))
    }

    @Test
    fun sendsBearerAndStableIdempotencyIdToChatAndVisionThenLogoutRevokesBoth() = runTest {
        val subject = "google:canonical"
        val transport = RecordingTransport(
            BackendHttpResponse(200, authResponse(subject, now + 900, 900)),
            BackendHttpResponse(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"Consejo seguro"}]}}]}"""
            ),
            BackendHttpResponse(503, "sensitive upstream response must be discarded")
        )
        val session = InMemoryBackendSession()
        val ids = ArrayDeque(
            listOf(
                "123e4567-e89b-42d3-a456-426614174000",
                "123e4567-e89b-42d3-a456-426614174001",
                "123e4567-e89b-42d3-a456-426614174002"
            )
        )
        val client = BackendApiClient(
            baseUrl = "https://api.fitplan.mx/",
            debugBuild = false,
            session = session,
            transport = transport,
            nowEpochSeconds = { now },
            requestIdFactory = { ids.removeFirst() }
        )
        client.exchangeGoogleIdToken("id-token").getOrThrow()

        assertEquals("Consejo seguro", client.requestPreventiveAdvice("prompt privado").getOrThrow())
        val visionResult = client.estimateFoodFromImage(testImage())
        assertTrue(visionResult.isFailure)

        val chat = transport.requests[1]
        assertEquals("https://api.fitplan.mx/api/gemini/chat", chat.url)
        assertEquals("Bearer ${session.bearerToken(now)}", chat.headers["Authorization"])
        assertEquals(chat.headers["X-Correlation-ID"], chat.headers["Idempotency-Key"])
        assertTrue(chat.body.contains("prompt privado"))

        val vision = transport.requests[2]
        assertEquals("https://api.fitplan.mx/api/gemini/vision", vision.url)
        assertEquals("Bearer ${session.bearerToken(now)}", vision.headers["Authorization"])
        assertEquals(vision.headers["X-Correlation-ID"], vision.headers["Idempotency-Key"])
        assertTrue(vision.body.contains(vision.headers.getValue("X-Correlation-ID")))
        assertFalse(
            visionResult.exceptionOrNull()?.message.orEmpty()
                .contains("sensitive upstream", ignoreCase = true)
        )

        val requestCount = transport.requests.size
        session.clear()
        assertNull(session.bearerToken(now))
        assertTrue(client.requestPreventiveAdvice("after logout").isFailure)
        assertTrue(client.estimateFoodFromImage(testImage()).isFailure)
        assertEquals(requestCount, transport.requests.size)
    }

    @Test
    fun authenticatedAccountDeletionUsesDeleteAndClearsTheBackendSession() = runTest {
        val subject = "google:delete-subject"
        val transport = RecordingTransport(
            BackendHttpResponse(200, authResponse(subject, now + 900, 900)),
            BackendHttpResponse(204, "")
        )
        val session = InMemoryBackendSession()
        val client = client(transport, session)
        client.exchangeGoogleIdToken("id-token").getOrThrow()

        assertTrue(client.deleteAccount().isSuccess)

        val deletion = transport.requests.last()
        assertEquals(BackendHttpMethod.DELETE, deletion.method)
        assertEquals("https://api.fitplan.mx/api/account", deletion.url)
        assertTrue(deletion.body.isEmpty())
        assertTrue(deletion.headers.getValue("Authorization").startsWith("Bearer eyJ"))
        assertNull(session.bearerToken(now))
    }

    @Test
    fun accountDeletionWithoutBackendSessionFailsClosedWithoutCallingTransport() = runTest {
        val transport = RecordingTransport()
        val session = InMemoryBackendSession()

        val result = client(transport, session).deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BackendAccountDeletionException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun failedRemoteAccountDeletionStillClearsTheBackendSession() = runTest {
        val subject = "google:delete-subject"
        val transport = RecordingTransport(
            BackendHttpResponse(200, authResponse(subject, now + 900, 900)),
            BackendHttpResponse(503, "sensitive upstream response")
        )
        val session = InMemoryBackendSession()
        val client = client(transport, session)
        client.exchangeGoogleIdToken("id-token").getOrThrow()

        val result = client.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BackendAccountDeletionException)
        assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("sensitive upstream"))
        assertNull(session.bearerToken(now))
    }

    @Test
    fun releaseRequiresHttpsAndDebugCleartextIsLimitedToLocalDevelopmentHosts() {
        assertEquals("https://api.fitplan.mx", normalizeBackendBaseUrl("https://api.fitplan.mx/", false))
        assertEquals("http://10.0.2.2:3000", normalizeBackendBaseUrl("http://10.0.2.2:3000/", true))
        assertEquals("http://localhost:3000", normalizeBackendBaseUrl("http://localhost:3000", true))

        assertTrue(runCatching { normalizeBackendBaseUrl("http://10.0.2.2:3000", false) }.isFailure)
        assertTrue(runCatching { normalizeBackendBaseUrl("http://api.fitplan.mx", true) }.isFailure)
        assertTrue(runCatching { normalizeBackendBaseUrl("https://user:pass@api.fitplan.mx", false) }.isFailure)
    }

    private fun client(
        transport: BackendHttpTransport,
        session: InMemoryBackendSession
    ) = BackendApiClient(
        baseUrl = "https://api.fitplan.mx",
        debugBuild = false,
        session = session,
        transport = transport,
        nowEpochSeconds = { now },
        requestIdFactory = { "123e4567-e89b-42d3-a456-426614174000" }
    )

    private fun authResponse(
        responseSubject: String,
        expiresAt: Long,
        expiresIn: Int,
        claimSubject: String = responseSubject
    ): String {
        val header = encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encode("""{"sub":"$claimSubject","exp":$expiresAt}""")
        return """{"accessToken":"$header.$payload.signature","tokenType":"Bearer","expiresIn":$expiresIn,"subject":"$responseSubject"}"""
    }

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun testImage() = GeminiImageRequest(
        imageBytes = byteArrayOf(1, 2, 3),
        originalBytes = 3,
        originalWidth = 1,
        originalHeight = 1,
        preparedWidth = 1,
        preparedHeight = 1,
        quality = 75
    )

    private class RecordingTransport(vararg responses: BackendHttpResponse) : BackendHttpTransport {
        val requests = mutableListOf<BackendHttpRequest>()
        private val responses = ArrayDeque(responses.toList())

        override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
