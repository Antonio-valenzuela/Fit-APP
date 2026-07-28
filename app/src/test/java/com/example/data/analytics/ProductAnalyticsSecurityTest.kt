package com.example.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ProductAnalyticsSecurityTest {
    @Before
    fun clearLogs() {
        ShadowLog.clear()
    }

    @Test
    fun allowedOperationalDebugEventNeverWritesPropertiesOrSensitiveValuesToLogcat() {
        ProductAnalytics.track(
            "onboarding_started",
            mapOf(
                "routine" to "private-routine-a",
                "duration" to "42",
                "latitude" to "19.4326",
                "longitude" to "-99.1332",
                "email" to "person.a@example.test",
                "routePointsJson" to "private-gps-route"
            )
        )

        val messages = ShadowLog.getLogsForTag("FitPlanAnalytics").map { it.msg }
        assertEquals(listOf("onboarding_started"), messages)
        val rendered = messages.joinToString()
        listOf(
            "private-routine-a",
            "42",
            "19.4326",
            "-99.1332",
            "person.a@example.test",
            "private-gps-route",
            "routine",
            "latitude",
            "longitude"
        ).forEach { sensitive ->
            assertFalse("Logcat leaked $sensitive", rendered.contains(sensitive))
        }
    }

    @Test
    fun healthLocationAndBehaviorEventNamesAreNotLogged() {
        listOf(
            "gps_session_logged",
            "goal_updated",
            "nutrition_logged",
            "recovery_signal_logged",
            "water_logged",
            "weight_logged",
            "wellbeing_entry_added",
            "workout_completed"
        ).forEach { event -> ProductAnalytics.track(event) }

        assertTrue(ShadowLog.getLogsForTag("FitPlanAnalytics").isEmpty())
    }

    @Test
    fun unknownOrUserControlledEventNameIsNotLogged() {
        ProductAnalytics.track("person.a@example.test")

        assertTrue(ShadowLog.getLogsForTag("FitPlanAnalytics").isEmpty())
    }

    @Test
    fun releaseBuildHasAnExplicitNoLogcatGuard() {
        val source = File("src/main/java/com/example/data/analytics/ProductAnalytics.kt").readText()

        assertTrue(source.contains("if (!BuildConfig.DEBUG) return"))
        assertFalse(source.contains("properties.entries"))
        assertFalse(source.contains("\$event\$payload"))
    }
}
