package com.example.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleAuthProfile(
    val email: String,
    val name: String,
    val idToken: String
)

class GoogleAuthManager(private val context: Context) {
    suspend fun signIn(): Result<GoogleAuthProfile> = withContext(Dispatchers.Main) {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (!isConfiguredGoogleWebClientId(webClientId)) {
            return@withContext Result.failure(
                IllegalStateException("Configura GOOGLE_WEB_CLIENT_ID para activar Google Sign-In.")
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleCredential.id
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Token de Google vacio."))
                }
                val name = googleCredential.displayName?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")
                Result.success(
                    GoogleAuthProfile(
                        email = email,
                        name = name,
                        idToken = idToken
                    )
                )
            } else {
                Result.failure(IllegalStateException("La credencial de Google no fue reconocida."))
            }
        } catch (error: GetCredentialException) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

internal fun isConfiguredGoogleWebClientId(webClientId: String): Boolean {
    val normalized = webClientId.trim()
    return normalized.isNotBlank() &&
        normalized != "MY_GOOGLE_WEB_CLIENT_ID" &&
        !normalized.contains("placeholder", ignoreCase = true) &&
        normalized.endsWith(".apps.googleusercontent.com")
}
