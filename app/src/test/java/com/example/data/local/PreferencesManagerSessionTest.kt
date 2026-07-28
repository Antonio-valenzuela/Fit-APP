package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PreferencesManagerSessionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPreferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPreferences().edit().clear().commit()
    }

    @Test
    fun localIdentityIsOpaqueStableAndKeepsEachUsersPreferencesIsolated() {
        val preferences = PreferencesManager(context)

        startLocal(preferences, "  Person.A@Example.COM ", "123456")
        val ownerA = activeOwnerId(preferences)
        assertNotNull(ownerA)
        assertFalse(ownerA!!.contains("person.a", ignoreCase = true))
        assertNotEquals("local_user", ownerA)
        assertEquals("LOCAL_ACCOUNT", sessionMode(preferences))
        preferences.setOnboardingCompleted(true)

        preferences.endSession()
        assertFalse(preferences.isLoggedIn())
        assertNull(activeOwnerId(preferences))
        assertNull(preferences.getUserEmail())

        startLocal(preferences, "person.b@example.com", "654321")
        val ownerB = activeOwnerId(preferences)
        assertNotNull(ownerB)
        assertNotEquals(ownerA, ownerB)
        assertFalse(preferences.isOnboardingCompleted())

        preferences.endSession()
        assertSecurityFailure {
            startLocal(preferences, "person.a@example.com", "000000")
        }
        assertFalse(preferences.isLoggedIn())
        assertNull(activeOwnerId(preferences))

        startLocal(preferences, "person.a@example.com", "123456")
        assertEquals(ownerA, activeOwnerId(preferences))
        assertTrue(preferences.isOnboardingCompleted())
    }

    @Test
    fun legacyDataIsBoundOnlyToThePreUpgradeIdentity() {
        rawPreferences().edit()
            .putBoolean("is_logged_in", true)
            .putString("user_email", "legacy@example.com")
            .commit()

        val preferences = PreferencesManager(context)
        assertFalse(preferences.isLoggedIn())
        assertNull(activeOwnerId(preferences))

        val legacyOwner = startLocal(preferences, "legacy@example.com", "112233")
        assertTrue(legacyOwner.startsWith("legacy_owner"))

        preferences.endSession()
        startLocal(preferences, "next@example.com", "445566")
        assertNotEquals(legacyOwner, activeOwnerId(preferences))

        preferences.endSession()
        startLocal(preferences, "legacy@example.com", "112233")
        assertEquals(legacyOwner, activeOwnerId(preferences))
    }

    @Test
    fun localPinUsesStrongSaltedHashAndNeverPersistsPlaintext() {
        val preferences = PreferencesManager(context)

        startLocal(preferences, "person.a@example.com", "123456")

        val persisted = rawPreferences().all
        assertFalse(persisted.keys.any { key -> key.contains("123456") })
        assertFalse(persisted.values.any { value -> value.toString().contains("123456") })
        val source = java.io.File("src/main/java/com/example/data/local/PreferencesManager.kt").readText()
        assertTrue(source.contains("PBKDF2WithHmacSHA256"))
        assertTrue(source.contains("SecureRandom"))
        assertTrue(source.contains("MessageDigest.isEqual"))
    }

    @Test
    fun pinMustContainAtLeastSixDigits() {
        val preferences = PreferencesManager(context)

        assertArgumentFailure { startLocal(preferences, "a@example.test", "12345") }
        assertArgumentFailure { startLocal(preferences, "a@example.test", "abcdef") }
        assertFalse(preferences.isLoggedIn())
    }

    @Test
    fun googleCredentialCreatesAnExplicitGoogleLocalSession() {
        val preferences = PreferencesManager(context)

        val ownerId = preferences.startGoogleLocalSession("google-subject-a", "a@example.test")

        assertTrue(ownerId.startsWith("google_"))
        assertEquals("GOOGLE_LOCAL", sessionMode(preferences))
    }

    @Test
    fun existingIdentityWithIncompletePinCredentialFailsClosed() {
        val preferences = PreferencesManager(context)
        startLocal(preferences, "person.a@example.test", "123456")
        preferences.endSession()

        val hashKey = rawPreferences().all.keys.single { it.startsWith("local_pin_hash_v1_") }
        rawPreferences().edit().remove(hashKey).commit()

        assertSecurityFailure {
            startLocal(PreferencesManager(context), "person.a@example.test", "654321")
        }
        assertFalse(PreferencesManager(context).isLoggedIn())
    }

    @Test
    fun repeatedWrongPinsArePersistentlyThrottledUntilBackoffExpires() {
        var nowMillis = 1_000L
        val preferences = PreferencesManager(context) { nowMillis }
        startLocal(preferences, "person.a@example.test", "123456")
        preferences.endSession()

        repeat(3) {
            assertSecurityFailure {
                startLocal(preferences, "person.a@example.test", "000000")
            }
        }
        assertSecurityFailure {
            startLocal(preferences, "person.a@example.test", "123456")
        }

        nowMillis += 1_000L
        startLocal(preferences, "person.a@example.test", "123456")
        assertTrue(preferences.isLoggedIn())
        assertFalse(rawPreferences().all.keys.any { it.startsWith("local_pin_failures_v1_") })
        assertFalse(rawPreferences().all.keys.any { it.startsWith("local_pin_blocked_until_v1_") })
    }

    @Test
    fun loggedOutLegacyUpgradeCannotBeClaimedByAnArbitraryEmail() {
        rawPreferences().edit()
            .putBoolean("is_logged_in", false)
            .commit()

        val preferences = PreferencesManager(context)
        val ownerId = startLocal(preferences, "attacker@example.test", "123456")

        assertTrue(ownerId.startsWith("local_"))
        assertNotEquals("legacy_owner_v1", ownerId)
    }

    @Test
    fun loggedOutLegacyUpgradeCanOnlyBeRecoveredByTheEmailStoredInTheMigratedProfile() {
        rawPreferences().edit()
            .putBoolean("is_logged_in", false)
            .commit()
        val preferences = PreferencesManager(context)

        preferences.authorizeLegacyRecoveryIfMatching(
            requestedEmail = "attacker@example.test",
            migratedProfileEmail = "legacy@example.test"
        )
        val attackerOwner = startLocal(preferences, "attacker@example.test", "123456")
        assertTrue(attackerOwner.startsWith("local_"))
        preferences.endSession()

        preferences.authorizeLegacyRecoveryIfMatching(
            requestedEmail = "legacy@example.test",
            migratedProfileEmail = "legacy@example.test"
        )
        val legacyOwner = startLocal(preferences, "legacy@example.test", "654321")
        assertEquals("legacy_owner_v1", legacyOwner)
    }

    private fun activeOwnerId(preferences: PreferencesManager): String? {
        val method = preferences.javaClass.methods.singleOrNull { method ->
            method.name == "getActiveOwnerId" && method.parameterCount == 0
        }
        assertNotNull("PreferencesManager must expose the active opaque owner", method)
        return requireNotNull(method).invoke(preferences) as String?
    }

    private fun startLocal(preferences: PreferencesManager, email: String, pin: String): String {
        val method = preferences.javaClass.methods.singleOrNull { candidate ->
            candidate.name == "startLocalAccountSession" && candidate.parameterCount == 2
        }
        assertNotNull("Local profiles must require email and PIN", method)
        return try {
            requireNotNull(method).invoke(preferences, email, pin) as String
        } catch (wrapped: java.lang.reflect.InvocationTargetException) {
            throw requireNotNull(wrapped.targetException)
        }
    }

    private fun sessionMode(preferences: PreferencesManager): String {
        val method = preferences.javaClass.methods.singleOrNull { method ->
            method.name == "getSessionMode" && method.parameterCount == 0
        }
        assertNotNull("PreferencesManager must distinguish local session modes", method)
        return requireNotNull(requireNotNull(method).invoke(preferences)).toString()
    }

    private fun assertSecurityFailure(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("Expected SecurityException, got $thrown", thrown is SecurityException)
    }

    private fun assertArgumentFailure(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("Expected IllegalArgumentException, got $thrown", thrown is IllegalArgumentException)
    }

    private fun rawPreferences() = context.getSharedPreferences(
        "fitplan_mx_preferences",
        Context.MODE_PRIVATE
    )
}
