package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackendWiringSecurityContractTest {
    @Test
    fun backendCredentialIsMemoryOnlyAndNeverLoggedOrPersisted() {
        val source = File("src/main/java/com/example/data/api/BackendApiClient.kt").readText()

        assertTrue(source.contains("class InMemoryBackendSession"))
        assertFalse(source.contains("SharedPreferences"))
        assertFalse(source.contains("Room"))
        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("println("))
        assertFalse(source.contains("BuildConfig.JWT"))
        assertFalse(source.contains("BuildConfig.ACCESS_TOKEN"))
    }

    @Test
    fun viewModelVerifiesBackendBeforeActivatingCanonicalGoogleIdentityAndClearsLogout() {
        val source = File("src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt").readText()
        val login = File("src/main/java/com/example/ui/screens/LoginScreen.kt").readText()

        assertTrue(source.contains("suspend fun loginGoogleUser"))
        assertTrue(source.indexOf("exchangeGoogleIdToken(idToken)") < source.indexOf("startGoogleLocalSession(identity.subject"))
        assertTrue(source.contains("backendSession.clear()"))
        assertTrue(login.contains("idToken = credential.idToken"))
        assertFalse(login.contains("providerSubject = profile.email"))
        assertFalse(login.contains("error.localizedMessage"))
    }

    @Test
    fun productionGeminiWiringUsesRealBackendAndNoDisabledDefault() {
        val service = File("src/main/java/com/example/data/api/GeminiService.kt").readText()
        val viewModel = File("src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt").readText()

        assertFalse(service.contains("= DisabledControlledBackendAiProvider"))
        assertTrue(viewModel.contains("GeminiService(backendApiClient)"))
        assertTrue(viewModel.contains("BackendApiClient.production(backendSession)"))
        assertTrue(File("src/main/java/com/example/data/api/BackendApiClient.kt").readText()
            .contains("BuildConfig.BACKEND_BASE_URL"))
    }
}
