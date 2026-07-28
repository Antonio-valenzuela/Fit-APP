package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalScreenTraversalInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchControlledSession() {
        seedSession()
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
    fun opensDashboardHealthNutritionWorkoutAndMuscleActions() {
        composeRule.onNodeWithTag("dashboard_quick_Agua").performClick()
        composeRule.onNodeWithTag("health_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("add_health_entry_button").performClick()
        composeRule.onNodeWithTag("health_entry_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithTag("bottom_nav_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_quick_Comida").performClick()
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("open_favorites_button").performClick()
        composeRule.onNodeWithTag("favorites_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Cerrar").performClick()
        waitUntilTextAbsent("Alimentos favoritos")

        composeRule.onNodeWithTag("bottom_nav_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_quick_Comida").performClick()
        composeRule.onNodeWithTag("nutrition_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open_frequent_button").performScrollTo().performClick()
        waitUntilTagDisplayed("frequent_dialog")
        composeRule.onNodeWithText("Cerrar").performClick()
        composeRule.onNodeWithTag("primary_register_food_button").performClick()
        composeRule.onNodeWithTag("food_entry_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        composeRule.onNodeWithTag("workout_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_muscle_map").performClick()
        composeRule.onNodeWithTag("muscle_map_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Regresar").performClick()
        composeRule.onNodeWithTag("progress_screen").assertIsDisplayed()
    }

    @Test
    fun opensSettingsPerformancePremiumAndSafeDialogs() {
        openSettings()

        scrollToTag("settings_open_performance")
        composeRule.onNodeWithTag("settings_open_performance").performClick()
        composeRule.onNodeWithTag("performance_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Regresar a ajustes").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        scrollToTag("settings_open_premium")
        composeRule.onNodeWithTag("settings_open_premium").performClick()
        composeRule.onNodeWithTag("premium_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("premium_test_mode_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Activar Premium", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Volver al Inicio").performClick()
        waitForTag("dashboard_screen")

        openSettings()
        scrollToTag("settings_open_health_notice")
        composeRule.onNodeWithTag("settings_open_health_notice").performClick()
        composeRule.onNodeWithText("Aviso de salud").assertIsDisplayed()
        composeRule.onNodeWithText("Entendido").performClick()

        scrollToTag("settings_open_delete_dialog")
        composeRule.onNodeWithTag("settings_open_delete_dialog").performClick()
        composeRule.onNodeWithTag("delete_data_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun logoutReturnsToLoginWithoutUsingDestructiveDataActions() {
        composeRule.onNodeWithTag("bottom_nav_profile").performClick()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        scrollToTag("logout_button")
        composeRule.onNodeWithTag("logout_button").performClick()
        waitForTag("login_screen")
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
    }

    private fun openSettings() {
        composeRule.onNodeWithTag("bottom_nav_profile").performClick()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("open_settings_button"))
        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    private fun scrollToTag(tag: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilTextAbsent(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitUntilTagDisplayed(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun seedSession() {
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
    }
}
