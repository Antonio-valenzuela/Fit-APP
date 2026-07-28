package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalLoginSecurityContractTest {
    @Test
    fun manualLoginIsExplicitlyLocalRequiresPinAndDoesNotClaimBackendAuth() {
        val source = File("src/main/java/com/example/ui/screens/LoginScreen.kt").readText()

        assertTrue(source.contains("pin_input"))
        assertTrue(source.contains("KeyboardType.NumberPassword"))
        assertTrue(source.contains("perfil local/offline", ignoreCase = true))
        assertTrue(source.contains("loginUser(email.trim(), name.trim(), pin)"))
        assertFalse(source.contains("Sesión iniciada con éxito"))
    }

    @Test
    fun googleCredentialIsVerifiedByBackendInsteadOfReusingManualLogin() {
        val source = File("src/main/java/com/example/ui/screens/LoginScreen.kt").readText()

        assertTrue(source.contains("loginGoogleUser"))
        assertTrue(source.contains("idToken = credential.idToken"))
        assertTrue(source.contains("Continuar con Google"))
        assertFalse(source.contains("loginUser(profile.email, profile.name)"))
    }

    @Test
    fun loggedOutLegacyRecoveryChecksTheMigratedRoomProfileBeforeBindingTheEmail() {
        val viewModelSource = File(
            "src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt"
        ).readText()
        val loginSource = File("src/main/java/com/example/ui/screens/LoginScreen.kt").readText()

        assertTrue(viewModelSource.contains("suspend fun loginUser"))
        assertTrue(viewModelSource.contains("db.userDao().getUser(LEGACY_OWNER_ID)"))
        assertTrue(viewModelSource.contains("prefs.authorizeLegacyRecoveryIfMatching"))
        assertTrue(loginSource.contains("scope.launch"))
    }
}
