package com.example.ui.viewmodel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class AccountDeletionCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun localAccountSkipsBackendAndPurgesEveryLocalStore() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = AccountDeletionCoordinator(
            deleteRemoteAccount = { calls += "remote"; Result.success(Unit) },
            wipeRoom = { calls += "room" },
            clearPreferences = { calls += "preferences" },
            clearCaches = { calls += "caches" },
            clearRemoteSession = { calls += "session" }
        )

        val result = coordinator.delete(requiresRemoteDeletion = false)

        assertTrue(result.isSuccess)
        assertEquals(listOf("room", "preferences", "caches", "session"), calls)
    }

    @Test
    fun remoteFailureKeepsLocalDataAndPropagatesFailureAfterClearingSession() = runTest {
        val failure = IllegalStateException("remote deletion failed")
        val calls = mutableListOf<String>()
        val coordinator = AccountDeletionCoordinator(
            deleteRemoteAccount = { calls += "remote"; Result.failure(failure) },
            wipeRoom = { calls += "room" },
            clearPreferences = { calls += "preferences" },
            clearCaches = { calls += "caches" },
            clearRemoteSession = { calls += "session" }
        )

        val result = coordinator.delete(requiresRemoteDeletion = true)

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(listOf("remote", "session"), calls)
    }

    @Test
    fun successfulRemoteDeletionWaitsBeforePurgingLocalData() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = AccountDeletionCoordinator(
            deleteRemoteAccount = { calls += "remote"; Result.success(Unit) },
            wipeRoom = { calls += "room" },
            clearPreferences = { calls += "preferences" },
            clearCaches = { calls += "caches" },
            clearRemoteSession = { calls += "session" }
        )

        val result = coordinator.delete(requiresRemoteDeletion = true)

        assertTrue(result.isSuccess)
        assertEquals(listOf("remote", "room", "preferences", "caches", "session"), calls)
    }

    @Test
    fun localCleanupContinuesAfterOneStoreFailsAndReturnsTheFailure() = runTest {
        val failure = IllegalStateException("room deletion failed")
        val calls = mutableListOf<String>()
        val coordinator = AccountDeletionCoordinator(
            deleteRemoteAccount = { Result.success(Unit) },
            wipeRoom = { calls += "room"; throw failure },
            clearPreferences = { calls += "preferences" },
            clearCaches = { calls += "caches" },
            clearRemoteSession = { calls += "session" }
        )

        val result = coordinator.delete(requiresRemoteDeletion = false)

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(listOf("room", "preferences", "caches", "session"), calls)
    }

    @Test
    fun cachePurgeDeletesOnlyReportsAndFoodPhotos() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val reports = cacheDir.resolve("reports").apply { mkdirs() }
        val foodPhotos = cacheDir.resolve("food_photos").apply { mkdirs() }
        val unrelated = cacheDir.resolve("unrelated").apply { mkdirs() }
        reports.resolve("health.pdf").writeText("private")
        foodPhotos.resolve("meal.jpg").writeText("private")
        unrelated.resolve("keep.txt").writeText("keep")

        clearSensitiveAccountCaches(cacheDir)

        assertFalse(reports.exists())
        assertFalse(foodPhotos.exists())
        assertTrue(unrelated.resolve("keep.txt").exists())
    }
}
