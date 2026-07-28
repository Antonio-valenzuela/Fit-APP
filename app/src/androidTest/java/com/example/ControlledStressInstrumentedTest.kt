package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlledStressInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchControlledSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("onboarding_completed", true)
            .putBoolean("feature_nutrition_enabled", true)
            .putBoolean("feature_workout_enabled", true)
            .putBoolean("feature_progress_enabled", true)
            .putBoolean("feature_health_enabled", true)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun changesBottomTabsThirtyTimesWithoutAmbiguousInput() {
        val destinations = listOf(
            "bottom_nav_dashboard" to "dashboard_screen",
            "bottom_nav_workout" to "workout_screen",
            "bottom_nav_nutrition" to "nutrition_screen",
            "bottom_nav_muscle_map" to "muscle_map_screen",
            "bottom_nav_profile" to "profile_screen"
        )

        repeat(6) {
            destinations.forEach { (navigationTag, destinationTag) ->
                composeRule.onNodeWithTag(navigationTag).performClick()
                composeRule.onNodeWithTag(destinationTag).assertIsDisplayed()
            }
        }
    }

    @Test
    fun opensProgressAndMuscleMapTwentyTimesEach() {
        repeat(20) {
            composeRule.onNodeWithTag("bottom_nav_workout").performClick()
            composeRule.onNodeWithTag("workout_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("open_progress_button").performClick()
            composeRule.onNodeWithTag("progress_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("open_muscle_map_button").performClick()
            composeRule.onNodeWithTag("muscle_map_screen").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Regresar").performClick()
            composeRule.onNodeWithTag("progress_screen").assertIsDisplayed()
        }
    }

    @Test
    fun recreatesActivityFiveTimesWithoutDuplicateDestination() {
        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()

        repeat(5) {
            scenario.recreate()
            waitForTag("nutrition_screen")
            composeRule.onAllNodesWithTag("nutrition_screen").assertCountEquals(1)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }
}
