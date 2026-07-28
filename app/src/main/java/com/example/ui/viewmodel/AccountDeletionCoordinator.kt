package com.example.ui.viewmodel

import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

internal class AccountDeletionCoordinator(
    private val deleteRemoteAccount: suspend () -> Result<Unit>,
    private val wipeRoom: suspend () -> Unit,
    private val clearPreferences: suspend () -> Unit,
    private val clearCaches: suspend () -> Unit,
    private val clearRemoteSession: () -> Unit
) {
    suspend fun delete(requiresRemoteDeletion: Boolean): Result<Unit> {
        try {
            if (requiresRemoteDeletion) {
                val remoteResult = deleteRemoteAccount()
                if (remoteResult.isFailure) return remoteResult
            }

            var firstFailure: Throwable? = null
            suspend fun attempt(block: suspend () -> Unit) {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (firstFailure == null) {
                        firstFailure = error
                    } else {
                        firstFailure?.addSuppressed(error)
                    }
                }
            }

            attempt(wipeRoom)
            attempt(clearPreferences)
            attempt(clearCaches)

            return firstFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
        } finally {
            clearRemoteSession()
        }
    }
}

internal fun clearSensitiveAccountCaches(cacheDir: File) {
    listOf("reports", "food_photos").forEach { directoryName ->
        val directory = File(cacheDir, directoryName)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("No se pudo eliminar la caché privada de la cuenta")
        }
    }
}
