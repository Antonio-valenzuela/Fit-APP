package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.ExerciseCatalogFeaturePolicy
import com.example.data.model.FeatureSettings
import com.example.data.model.HybridPlanSettings
import com.example.data.model.LEGACY_OWNER_ID
import com.example.data.model.isValidOwnerId
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LocalSessionMode {
    LOCAL_ACCOUNT,
    GOOGLE_LOCAL,
    LEGACY_LOCAL
}

class PreferencesManager internal constructor(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "fitplan_mx_preferences",
        Context.MODE_PRIVATE
    )

    init {
        if (!prefs.contains("initialized_performance_defaults")) {
            val isLowEnd = DevicePerformanceUtils.isLowEndDevice(context)
            prefs.edit().apply {
                putBoolean("initialized_performance_defaults", true)
                putBoolean("feature_performance_mode_enabled", isLowEnd)
                putBoolean("feature_animations_enabled", !isLowEnd)
                putBoolean("feature_charts_enabled", !isLowEnd)
                putBoolean("feature_ai_recommendations_enabled", false)
                putBoolean("feature_reminders_enabled", false)
                putBoolean("feature_nutrition_enabled", true)
                putBoolean("feature_workout_enabled", true)
                putBoolean("feature_progress_enabled", true)
                putBoolean("feature_health_enabled", true)
                putBoolean("feature_premium_enabled", false)
                apply()
            }
        }
        if (!prefs.contains("migrated_health_manual_default_v1")) {
            prefs.edit().apply {
                putBoolean("migrated_health_manual_default_v1", true)
                putBoolean("feature_health_enabled", true)
                apply()
            }
        }
        migrateLegacySessionIfNeeded()
        closeUnprotectedLocalSession()
    }

    fun getFeatureSettings(): FeatureSettings {
        return try {
            FeatureSettings(
                nutritionEnabled = prefs.getBoolean("feature_nutrition_enabled", true),
                workoutEnabled = prefs.getBoolean("feature_workout_enabled", true),
                progressEnabled = prefs.getBoolean("feature_progress_enabled", true),
                healthEnabled = prefs.getBoolean("feature_health_enabled", true),
                aiRecommendationsEnabled = prefs.getBoolean("feature_ai_recommendations_enabled", false),
                premiumEnabled = prefs.getBoolean("feature_premium_enabled", false),
                chartsEnabled = prefs.getBoolean("feature_charts_enabled", true),
                remindersEnabled = prefs.getBoolean("feature_reminders_enabled", false),
                animationsEnabled = prefs.getBoolean("feature_animations_enabled", false),
                performanceModeEnabled = prefs.getBoolean("feature_performance_mode_enabled", true),
                streakEnabled = prefs.getBoolean("feature_streak_enabled", true),
                showStreakWidget = prefs.getBoolean("feature_show_streak_widget", true),
                dailyMissionsEnabled = prefs.getBoolean("feature_daily_missions_enabled", true),
                achievementsEnabled = prefs.getBoolean("feature_achievements_enabled", true),
                motivationMessagesEnabled = prefs.getBoolean("feature_motivation_messages_enabled", true),
                softMotivationMode = prefs.getBoolean("feature_soft_motivation_mode", true),
                exerciseCatalogEnabled = ExerciseCatalogFeaturePolicy.resolve(
                    buildEnabled = BuildConfig.EXERCISE_CATALOG_ENABLED,
                    storedOverride = readExerciseCatalogOverride()
                )
            )
        } catch (error: ClassCastException) {
            Log.e("PreferencesManager", "Feature settings were corrupted; using defaults", error)
            FeatureSettings(
                exerciseCatalogEnabled = ExerciseCatalogFeaturePolicy.resolve(
                    buildEnabled = BuildConfig.EXERCISE_CATALOG_ENABLED,
                    storedOverride = readExerciseCatalogOverride()
                )
            )
        }
    }

    private fun readExerciseCatalogOverride(): Boolean? {
        if (!prefs.contains(EXERCISE_CATALOG_OVERRIDE_KEY)) return null
        return try {
            prefs.getBoolean(EXERCISE_CATALOG_OVERRIDE_KEY, true)
        } catch (error: ClassCastException) {
            Log.e("PreferencesManager", "Exercise catalog override was corrupted; using build default", error)
            null
        }
    }

    fun updateFeatureSettings(settings: FeatureSettings) {
        prefs.edit().apply {
            putBoolean("feature_nutrition_enabled", settings.nutritionEnabled)
            putBoolean("feature_workout_enabled", settings.workoutEnabled)
            putBoolean("feature_progress_enabled", settings.progressEnabled)
            putBoolean("feature_health_enabled", settings.healthEnabled)
            putBoolean("feature_ai_recommendations_enabled", settings.aiRecommendationsEnabled)
            putBoolean("feature_premium_enabled", settings.premiumEnabled)
            putBoolean("feature_charts_enabled", settings.chartsEnabled)
            putBoolean("feature_reminders_enabled", settings.remindersEnabled)
            putBoolean("feature_animations_enabled", settings.animationsEnabled)
            putBoolean("feature_performance_mode_enabled", settings.performanceModeEnabled)
            putBoolean("feature_streak_enabled", settings.streakEnabled)
            putBoolean("feature_show_streak_widget", settings.showStreakWidget)
            putBoolean("feature_daily_missions_enabled", settings.dailyMissionsEnabled)
            putBoolean("feature_achievements_enabled", settings.achievementsEnabled)
            putBoolean("feature_motivation_messages_enabled", settings.motivationMessagesEnabled)
            putBoolean("feature_soft_motivation_mode", settings.softMotivationMode)
            putBoolean(EXERCISE_CATALOG_OVERRIDE_KEY, settings.exerciseCatalogEnabled)
            apply()
        }
    }

    fun setNutritionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_nutrition_enabled", enabled).apply()
    }

    fun setWorkoutEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_workout_enabled", enabled).apply()
    }

    fun setProgressEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_progress_enabled", enabled).apply()
    }

    fun setHealthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_health_enabled", enabled).apply()
    }

    fun setAIRecommendationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_ai_recommendations_enabled", enabled).apply()
    }

    fun setPremiumEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_premium_enabled", enabled).apply()
    }

    fun setChartsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_charts_enabled", enabled).apply()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_reminders_enabled", enabled).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_animations_enabled", enabled).apply()
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_performance_mode_enabled", enabled).apply()
    }

    fun setStreakEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_streak_enabled", enabled).apply()
    }

    fun setShowStreakWidget(enabled: Boolean) {
        prefs.edit().putBoolean("feature_show_streak_widget", enabled).apply()
    }

    fun setDailyMissionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_daily_missions_enabled", enabled).apply()
    }

    fun setAchievementsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_achievements_enabled", enabled).apply()
    }

    fun setMotivationMessagesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feature_motivation_messages_enabled", enabled).apply()
    }

    fun setSoftMotivationMode(enabled: Boolean) {
        prefs.edit().putBoolean("feature_soft_motivation_mode", enabled).apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(ACTIVE_SESSION_KEY, false) && getActiveOwnerId() != null
    }

    fun startLocalAccountSession(email: String, pin: String): String {
        val normalizedEmail = normalizeIdentity(email)
        require(normalizedEmail.isNotBlank()) { "A local identity email is required" }
        require(LOCAL_PIN_PATTERN.matches(pin)) { "El PIN local debe contener entre 6 y 12 dígitos" }
        val fingerprint = identityFingerprint(LocalSessionMode.LOCAL_ACCOUNT, normalizedEmail)
        val existingBinding = prefs.getString(bindingKey(fingerprint), null)
        val storedSalt = prefs.getString(pinSaltKey(fingerprint), null)
        val storedHash = prefs.getString(pinHashKey(fingerprint), null)
        val ownerId: String
        if (existingBinding == null) {
            if (storedSalt != null || storedHash != null) {
                throw SecurityException("Las credenciales locales están dañadas")
            }
            ownerId = "local_${fingerprint.take(40)}"
            enrollLocalPin(fingerprint, ownerId, pin)
        } else if (storedSalt == null || storedHash == null) {
            val authorizedLegacyEnrollment =
                existingBinding == LEGACY_OWNER_ID &&
                    storedSalt == null &&
                    storedHash == null &&
                    prefs.getBoolean(legacyEnrollmentKey(fingerprint), false)
            if (!authorizedLegacyEnrollment) {
                throw SecurityException("Las credenciales locales están dañadas")
            }
            ownerId = existingBinding
            enrollLocalPin(fingerprint, ownerId, pin)
        } else {
            ownerId = existingBinding
            enforcePinAttemptAllowed(fingerprint)
            val salt = decodeCredential(storedSalt)
            val expectedHash = decodeCredential(storedHash)
            val suppliedHash = derivePinHash(pin, salt)
            if (!MessageDigest.isEqual(expectedHash, suppliedHash)) {
                recordFailedPinAttempt(fingerprint)
                throw SecurityException("PIN local incorrecto")
            }
            clearFailedPinAttempts(fingerprint)
        }
        activateSession(ownerId, LocalSessionMode.LOCAL_ACCOUNT, normalizedEmail, fingerprint)
        return ownerId
    }

    private fun enrollLocalPin(fingerprint: String, ownerId: String, pin: String) {
            val salt = ByteArray(PIN_SALT_BYTES).also(SecureRandom()::nextBytes)
            val hash = derivePinHash(pin, salt)
            val committed = prefs.edit()
                .putString(bindingKey(fingerprint), ownerId)
                .putString(pinSaltKey(fingerprint), encode(salt))
                .putString(pinHashKey(fingerprint), encode(hash))
                .remove(legacyEnrollmentKey(fingerprint))
                .remove(pinFailuresKey(fingerprint))
                .remove(pinBlockedUntilKey(fingerprint))
                .commit()
            if (!committed) {
                throw SecurityException("No se pudo guardar la credencial local")
            }
        }

    fun startGoogleLocalSession(providerSubject: String, email: String): String {
        val normalizedSubject = normalizeIdentity(providerSubject)
        require(normalizedSubject.isNotBlank()) { "A Google local identity subject is required" }
        val fingerprint = identityFingerprint(LocalSessionMode.GOOGLE_LOCAL, normalizedSubject)
        val ownerId = prefs.getString(bindingKey(fingerprint), null)
            ?: "google_${fingerprint.take(40)}"
        prefs.edit().putString(bindingKey(fingerprint), ownerId).apply()
        activateSession(ownerId, LocalSessionMode.GOOGLE_LOCAL, normalizeIdentity(email), fingerprint)
        return ownerId
    }

    internal fun authorizeLegacyRecoveryIfMatching(
        requestedEmail: String,
        migratedProfileEmail: String?
    ) {
        if (!prefs.getBoolean(LEGACY_RECOVERY_PENDING_KEY, false)) return
        val requested = normalizeIdentity(requestedEmail)
        val migrated = migratedProfileEmail?.let(::normalizeIdentity).orEmpty()
        if (requested.isBlank() || requested != migrated) return
        val fingerprint = identityFingerprint(LocalSessionMode.LOCAL_ACCOUNT, migrated)
        val existingBinding = prefs.getString(bindingKey(fingerprint), null)
        if (existingBinding != null && existingBinding != LEGACY_OWNER_ID) {
            throw SecurityException("La identidad local ya está vinculada a otro perfil")
        }
        val committed = prefs.edit()
            .putString(bindingKey(fingerprint), LEGACY_OWNER_ID)
            .putBoolean(legacyEnrollmentKey(fingerprint), true)
            .remove(LEGACY_RECOVERY_PENDING_KEY)
            .commit()
        if (!committed) {
            throw SecurityException("No se pudo preparar la recuperación local")
        }
    }

    fun endSession() {
        prefs.edit().apply {
            putBoolean(ACTIVE_SESSION_KEY, false)
            remove(ACTIVE_OWNER_KEY)
            remove(ACTIVE_EMAIL_KEY)
            remove(ACTIVE_IDENTITY_FINGERPRINT_KEY)
            remove(SESSION_MODE_KEY)
            apply()
        }
    }

    fun getUserEmail(): String? {
        if (!isLoggedIn()) return null
        return prefs.getString(ACTIVE_EMAIL_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun getActiveOwnerId(): String? {
        if (!prefs.getBoolean(ACTIVE_SESSION_KEY, false)) return null
        return prefs.getString(ACTIVE_OWNER_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun getSessionMode(): LocalSessionMode? {
        if (!isLoggedIn()) return null
        return prefs.getString(SESSION_MODE_KEY, null)?.let { stored ->
            LocalSessionMode.entries.firstOrNull { mode -> mode.name == stored }
        }
    }

    fun isOnboardingCompleted(): Boolean {
        return getOwnerBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        putOwnerBoolean("onboarding_completed", completed)
    }

    fun isPremiumUnlocked(): Boolean {
        return getOwnerBoolean("premium_unlocked", false)
    }

    fun setPremiumUnlocked(unlocked: Boolean) {
        putOwnerBoolean("premium_unlocked", unlocked)
    }

    fun useMetricSettings(): Boolean {
        return getOwnerBoolean("use_metric", true)
    }

    fun setUseMetricSettings(metric: Boolean) {
        putOwnerBoolean("use_metric", metric)
    }

    fun getThemeSelector(): String {
        return getOwnerString("theme_selector", "system")
    }

    fun setThemeSelector(theme: String) {
        putOwnerString("theme_selector", theme)
    }

    fun getDefaultGlassMl(): Int {
        return getOwnerInt("default_glass_ml", 250).coerceIn(100, 1000)
    }

    fun setDefaultGlassMl(value: Int) {
        putOwnerInt("default_glass_ml", value.coerceIn(100, 1000))
    }

    fun getHybridPlanSettings(): HybridPlanSettings {
        return HybridPlanSettings(
            strengthDaysPerWeek = getOwnerInt("hybrid_strength_days", 3).coerceIn(0, 7),
            cardioDaysPerWeek = getOwnerInt("hybrid_cardio_days", 2).coerceIn(0, 7),
            objective = getOwnerString("hybrid_objective", "Recomposicion"),
            level = getOwnerString("hybrid_level", "Principiante"),
            sessionMinutes = getOwnerInt("hybrid_session_minutes", 45).coerceIn(10, 120),
            runningEnabled = getOwnerBoolean("hybrid_running_enabled", true),
            walkingEnabled = getOwnerBoolean("hybrid_walking_enabled", true),
            cyclingEnabled = getOwnerBoolean("hybrid_cycling_enabled", true)
        )
    }

    fun setHybridPlanSettings(settings: HybridPlanSettings) {
        putOwnerInt("hybrid_strength_days", settings.strengthDaysPerWeek.coerceIn(0, 7))
        putOwnerInt("hybrid_cardio_days", settings.cardioDaysPerWeek.coerceIn(0, 7))
        putOwnerString("hybrid_objective", settings.objective)
        putOwnerString("hybrid_level", settings.level)
        putOwnerInt("hybrid_session_minutes", settings.sessionMinutes.coerceIn(10, 120))
        putOwnerBoolean("hybrid_running_enabled", settings.runningEnabled)
        putOwnerBoolean("hybrid_walking_enabled", settings.walkingEnabled)
        putOwnerBoolean("hybrid_cycling_enabled", settings.cyclingEnabled)
    }

    fun clearAllPreferences() {
        val ownerId = getActiveOwnerId()
        val storedFingerprint = prefs.getString(ACTIVE_IDENTITY_FINGERPRINT_KEY, null)
        val fallbackFingerprint = if (storedFingerprint == null) {
            val email = prefs.getString(ACTIVE_EMAIL_KEY, null)
                ?.let(::normalizeIdentity)
                ?.takeIf { it.isNotBlank() }
            val mode = prefs.getString(SESSION_MODE_KEY, null)
            if (email == null || mode == null) null else identityFingerprint(
                if (mode == LocalSessionMode.GOOGLE_LOCAL.name) {
                    LocalSessionMode.GOOGLE_LOCAL
                } else {
                    LocalSessionMode.LOCAL_ACCOUNT
                },
                email
            )
        } else {
            storedFingerprint
        }
        val editor = prefs.edit()
        if (ownerId != null) {
            val prefix = "$ownerId::"
            prefs.all.keys.filter { key -> key.startsWith(prefix) }.forEach(editor::remove)
            if (ownerId == LEGACY_OWNER_ID) {
                LEGACY_OWNER_PREFERENCE_KEYS.forEach(editor::remove)
            }
        }
        if (fallbackFingerprint != null) {
            editor
                .remove(bindingKey(fallbackFingerprint))
                .remove(pinSaltKey(fallbackFingerprint))
                .remove(pinHashKey(fallbackFingerprint))
                .remove(legacyEnrollmentKey(fallbackFingerprint))
                .remove(pinFailuresKey(fallbackFingerprint))
                .remove(pinBlockedUntilKey(fallbackFingerprint))
        }
        val committed = editor
            .putBoolean(ACTIVE_SESSION_KEY, false)
            .remove(ACTIVE_OWNER_KEY)
            .remove(ACTIVE_EMAIL_KEY)
            .remove(ACTIVE_IDENTITY_FINGERPRINT_KEY)
            .remove(SESSION_MODE_KEY)
            .commit()
        if (!committed) {
            throw SecurityException("No se pudieron eliminar las credenciales locales")
        }
    }

    private fun migrateLegacySessionIfNeeded() {
        if (prefs.getBoolean(SESSION_MIGRATED_KEY, false)) return
        val wasLoggedIn = prefs.getBoolean("is_logged_in", false)
        val legacyEmail = prefs.getString("user_email", null)?.trim().orEmpty()
        val editor = prefs.edit().putBoolean(SESSION_MIGRATED_KEY, true)
        if (wasLoggedIn && legacyEmail.isNotBlank()) {
            val normalizedEmail = normalizeIdentity(legacyEmail)
            val fingerprint = identityFingerprint(LocalSessionMode.LOCAL_ACCOUNT, normalizedEmail)
            editor
                .putString(bindingKey(fingerprint), LEGACY_OWNER_ID)
                .putBoolean(legacyEnrollmentKey(fingerprint), true)
                .remove(LEGACY_RECOVERY_PENDING_KEY)
        } else {
            editor.putBoolean(LEGACY_RECOVERY_PENDING_KEY, true)
        }
        editor.remove("is_logged_in").remove("user_email").commit()
    }

    private fun closeUnprotectedLocalSession() {
        if (!prefs.getBoolean(ACTIVE_SESSION_KEY, false)) return
        val storedMode = prefs.getString(SESSION_MODE_KEY, null)
        if (storedMode != LocalSessionMode.LOCAL_ACCOUNT.name &&
            storedMode != LocalSessionMode.LEGACY_LOCAL.name
        ) return
        val normalizedEmail = prefs.getString(ACTIVE_EMAIL_KEY, null)
            ?.let(::normalizeIdentity)
            ?.takeIf { it.isNotBlank() }
        if (normalizedEmail == null) {
            endSession()
            return
        }
        val fingerprint = identityFingerprint(LocalSessionMode.LOCAL_ACCOUNT, normalizedEmail)
        if (!prefs.contains(pinSaltKey(fingerprint)) || !prefs.contains(pinHashKey(fingerprint))) {
            endSession()
        }
    }

    private fun activateSession(
        ownerId: String,
        mode: LocalSessionMode,
        email: String,
        fingerprint: String
    ) {
        check(isValidOwnerId(ownerId)) { "Invalid local owner" }
        prefs.edit()
            .putBoolean(ACTIVE_SESSION_KEY, true)
            .putString(ACTIVE_OWNER_KEY, ownerId)
            .putString(ACTIVE_EMAIL_KEY, email)
            .putString(ACTIVE_IDENTITY_FINGERPRINT_KEY, fingerprint)
            .putString(SESSION_MODE_KEY, if (ownerId == LEGACY_OWNER_ID) LocalSessionMode.LEGACY_LOCAL.name else mode.name)
            .apply()
    }

    private fun normalizeIdentity(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun identityFingerprint(mode: LocalSessionMode, subject: String): String {
        val material = "${installationSalt()}|${mode.name}|$subject"
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeCredential(value: String): ByteArray = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (error: IllegalArgumentException) {
        throw SecurityException("Las credenciales locales están dañadas", error)
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun enforcePinAttemptAllowed(fingerprint: String) {
        val blockedUntil = prefs.getLong(pinBlockedUntilKey(fingerprint), 0L)
        if (blockedUntil > nowMillis()) {
            throw SecurityException("PIN local temporalmente bloqueado")
        }
    }

    private fun recordFailedPinAttempt(fingerprint: String) {
        val failures = (prefs.getInt(pinFailuresKey(fingerprint), 0) + 1).coerceAtMost(30)
        val exponent = (failures - PIN_BACKOFF_START_ATTEMPT).coerceIn(0, PIN_BACKOFF_MAX_EXPONENT)
        val delayMillis = if (failures < PIN_BACKOFF_START_ATTEMPT) {
            0L
        } else {
            (PIN_BACKOFF_BASE_MILLIS * (1L shl exponent)).coerceAtMost(PIN_BACKOFF_MAX_MILLIS)
        }
        val committed = prefs.edit()
            .putInt(pinFailuresKey(fingerprint), failures)
            .putLong(pinBlockedUntilKey(fingerprint), nowMillis() + delayMillis)
            .commit()
        if (!committed) {
            throw SecurityException("No se pudo proteger la credencial local")
        }
    }

    private fun clearFailedPinAttempts(fingerprint: String) {
        if (!prefs.contains(pinFailuresKey(fingerprint)) &&
            !prefs.contains(pinBlockedUntilKey(fingerprint))
        ) return
        val committed = prefs.edit()
            .remove(pinFailuresKey(fingerprint))
            .remove(pinBlockedUntilKey(fingerprint))
            .commit()
        if (!committed) {
            throw SecurityException("No se pudo actualizar la credencial local")
        }
    }

    private fun installationSalt(): String {
        prefs.getString(INSTALLATION_SALT_KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(INSTALLATION_SALT_KEY, generated).commit()
        return generated
    }

    private fun bindingKey(fingerprint: String): String = "identity_binding_v2_$fingerprint"
    private fun pinSaltKey(fingerprint: String): String = "local_pin_salt_v1_$fingerprint"
    private fun pinHashKey(fingerprint: String): String = "local_pin_hash_v1_$fingerprint"
    private fun legacyEnrollmentKey(fingerprint: String): String = "legacy_pin_enrollment_v1_$fingerprint"
    private fun pinFailuresKey(fingerprint: String): String = "local_pin_failures_v1_$fingerprint"
    private fun pinBlockedUntilKey(fingerprint: String): String = "local_pin_blocked_until_v1_$fingerprint"

    private fun ownerKey(key: String): String? = getActiveOwnerId()?.let { owner -> "$owner::$key" }

    private fun getOwnerBoolean(key: String, default: Boolean): Boolean {
        val scoped = ownerKey(key) ?: return default
        if (prefs.contains(scoped)) return prefs.getBoolean(scoped, default)
        return if (getActiveOwnerId() == LEGACY_OWNER_ID && prefs.contains(key)) prefs.getBoolean(key, default) else default
    }

    private fun getOwnerInt(key: String, default: Int): Int {
        val scoped = ownerKey(key) ?: return default
        if (prefs.contains(scoped)) return prefs.getInt(scoped, default)
        return if (getActiveOwnerId() == LEGACY_OWNER_ID && prefs.contains(key)) prefs.getInt(key, default) else default
    }

    private fun getOwnerString(key: String, default: String): String {
        val scoped = ownerKey(key) ?: return default
        if (prefs.contains(scoped)) return prefs.getString(scoped, default) ?: default
        return if (getActiveOwnerId() == LEGACY_OWNER_ID && prefs.contains(key)) prefs.getString(key, default) ?: default else default
    }

    private fun putOwnerBoolean(key: String, value: Boolean) {
        ownerKey(key)?.let { scoped -> prefs.edit().putBoolean(scoped, value).apply() }
    }

    private fun putOwnerInt(key: String, value: Int) {
        ownerKey(key)?.let { scoped -> prefs.edit().putInt(scoped, value).apply() }
    }

    private fun putOwnerString(key: String, value: String) {
        ownerKey(key)?.let { scoped -> prefs.edit().putString(scoped, value).apply() }
    }

    private companion object {
        const val EXERCISE_CATALOG_OVERRIDE_KEY = "feature_exercise_catalog_enabled"
        const val SESSION_MIGRATED_KEY = "session_identity_migrated_v2"
        const val LEGACY_RECOVERY_PENDING_KEY = "legacy_identity_recovery_pending_v1"
        const val INSTALLATION_SALT_KEY = "installation_identity_salt_v2"
        const val ACTIVE_SESSION_KEY = "active_local_session_v2"
        const val ACTIVE_OWNER_KEY = "active_owner_id_v2"
        const val ACTIVE_EMAIL_KEY = "active_local_email_v2"
        const val ACTIVE_IDENTITY_FINGERPRINT_KEY = "active_identity_fingerprint_v2"
        const val SESSION_MODE_KEY = "active_session_mode_v2"
        const val PIN_SALT_BYTES = 16
        const val PIN_HASH_ITERATIONS = 210_000
        const val PIN_HASH_BITS = 256
        const val PIN_BACKOFF_START_ATTEMPT = 3
        const val PIN_BACKOFF_BASE_MILLIS = 1_000L
        const val PIN_BACKOFF_MAX_MILLIS = 5 * 60 * 1_000L
        const val PIN_BACKOFF_MAX_EXPONENT = 8
        val LOCAL_PIN_PATTERN = Regex("^[0-9]{6,12}$")
        val LEGACY_OWNER_PREFERENCE_KEYS = setOf(
            "onboarding_completed",
            "premium_unlocked",
            "use_metric",
            "theme_selector",
            "default_glass_ml",
            "hybrid_strength_days",
            "hybrid_cardio_days",
            "hybrid_objective",
            "hybrid_level",
            "hybrid_session_minutes",
            "hybrid_running_enabled",
            "hybrid_walking_enabled",
            "hybrid_cycling_enabled"
        )
    }
}
