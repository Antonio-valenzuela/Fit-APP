package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchControlledSession() {
        seedControlledSession()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        waitForTag("bottom_nav_dashboard")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun opensCriticalDestinationsAndReturnsSafely() {
        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        composeRule.onNodeWithTag("workout_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("open_progress_button").performClick()
        composeRule.onNodeWithTag("progress_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open_muscle_map_button").performClick()
        composeRule.onNodeWithTag("muscle_map_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Regresar").performClick()
        composeRule.onNodeWithTag("progress_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_profile").performClick()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("open_settings_button"))
        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun doubleTapAndActivityRecreationDoNotDuplicateNavigation() {
        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("nutrition_screen").assertCountEquals(1)

        scenario.recreate()
        waitForAnyTag("nutrition_screen", "bottom_nav_dashboard")
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("nutrition_screen").assertCountEquals(1)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForAnyTag(vararg tags: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            tags.any { tag -> composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun seedControlledSession() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .putBoolean("onboarding_completed", true)
                .putBoolean("feature_nutrition_enabled", true)
                .putBoolean("feature_workout_enabled", true)
                .putBoolean("feature_progress_enabled", true)
                .putBoolean("feature_animations_enabled", false)
                .commit()
        }
    }
}
