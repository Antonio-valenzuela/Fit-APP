package com.example.data.api

import com.example.data.model.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiServiceSecurityTest {

    @Test
    fun remoteProviderFailureUsesOfflineAdvice() = runTest {
        val provider = RecordingRemoteAiProvider()
        val service = GeminiService(provider)

        val advice = service.getPreventiveAdvice(UserEntity(name = "Yahir"), emptyList(), "hábitos")

        assertTrue(advice.contains("FitCore AI"))
        assertEquals(1, provider.adviceRequests)
    }

    @Test
    fun unauthenticatedRealBackendUsesOfflineAdviceAndRejectsPhotoWithoutTransport() = runTest {
        val transport = BackendHttpTransport { error("No network request expected") }
        val provider = BackendApiClient(
            baseUrl = "https://api.fitplan.mx",
            debugBuild = false,
            session = InMemoryBackendSession(),
            transport = transport
        )
        val service = GeminiService(provider)

        val advice = service.getPreventiveAdvice(UserEntity(name = "Yahir"), emptyList(), "hábitos")
        val photoResult = service.estimateFoodFromImage(testImage())

        assertTrue(advice.contains("FitCore AI"))
        assertTrue(photoResult.isFailure)
        assertTrue(photoResult.exceptionOrNull() is ControlledBackendUnavailableException)
    }

    @Test
    fun localPinStyleSessionRejectsPhotoAnalysisWithoutFakeBackendAuthentication() = runTest {
        val provider = BackendApiClient(
            baseUrl = "https://api.fitplan.mx",
            debugBuild = false,
            session = InMemoryBackendSession(),
            transport = BackendHttpTransport { error("No network request expected") }
        )
        val result = GeminiService(provider).estimateFoodFromImage(testImage())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("desactivado", ignoreCase = true))
    }

    private fun testImage() = GeminiImageRequest(
            imageBytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalWidth = 1,
            originalHeight = 1,
            preparedWidth = 1,
            preparedHeight = 1,
            originalBytes = 3,
            quality = 75
        )

    private class RecordingRemoteAiProvider : ControlledBackendAiProvider {
        var adviceRequests = 0

        override suspend fun requestPreventiveAdvice(prompt: String): Result<String> {
            adviceRequests += 1
            return Result.failure(ControlledBackendUnavailableException())
        }

        override suspend fun estimateFoodFromImage(image: GeminiImageRequest): Result<GeminiNutritionResult> =
            Result.failure(ControlledBackendUnavailableException())
    }
}
