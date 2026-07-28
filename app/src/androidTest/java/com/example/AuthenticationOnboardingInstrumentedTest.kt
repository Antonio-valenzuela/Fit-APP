package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationOnboardingInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchLoggedOut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", false)
            .putBoolean("onboarding_completed", false)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("login_screen")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun emptyCredentialsRemainOnLoginAndShowBothErrors() {
        composeRule.onNodeWithTag("login_button").performClick()

        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Ingresa tu nombre").assertIsDisplayed()
        composeRule.onNodeWithText("Ingresa un correo válido").assertIsDisplayed()
    }

    @Test
    fun completesLocalLoginAndAllOnboardingSteps() {
        composeRule.onNodeWithTag("username_input").performTextReplacement("QA Huawei")
        composeRule.onNodeWithTag("email_input").performTextReplacement("qa.huawei@example.com")
        composeRule.onNodeWithTag("login_button").performClick()
        waitForTag("onboarding_step_1")

        composeRule.onNodeWithTag("onboarding_age").performTextReplacement("30")
        composeRule.onNodeWithTag("onboarding_weight").performTextReplacement("72")
        composeRule.onNodeWithTag("onboarding_height").performTextReplacement("175")
        composeRule.onNodeWithTag("onboarding_next_step_1").performClick()
        waitForTag("onboarding_step_2")

        composeRule.onNodeWithTag("onboarding_discipline_GYM").performClick()
        composeRule.onNodeWithTag("onboarding_goal_Ganar_musculo").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_next_step_2").performScrollTo().performClick()
        waitForTag("onboarding_step_3")

        composeRule.onNodeWithTag("onboarding_place_Casa").performClick()
        composeRule.onNodeWithTag("onboarding_next_step_3").performScrollTo().performClick()
        waitForTag("onboarding_step_4")

        composeRule.onNodeWithTag("onboarding_disclaimer").performClick()
        composeRule.onNodeWithTag("finish_onboarding_btn").performScrollTo().performClick()
        waitForTag("dashboard_screen")
    }

    @Test
    fun weeklyPlanSelectionsRecalculateDependentInformation() {
        composeRule.onNodeWithTag("username_input").performTextReplacement("QA Plan")
        composeRule.onNodeWithTag("email_input").performTextReplacement("qa.plan@example.com")
        composeRule.onNodeWithTag("login_button").performClick()
        waitForTag("onboarding_step_1")

        composeRule.onNodeWithTag("onboarding_next_step_1").performClick()
        waitForTag("onboarding_step_2")
        composeRule.onNodeWithTag("onboarding_next_step_2").performScrollTo().performClick()
        waitForTag("onboarding_step_3")

        composeRule.onNodeWithTag("onboarding_level_Avanzado").performClick()
        composeRule.onNodeWithTag("onboarding_intensity").assertTextContains("Alta")

        composeRule.onNodeWithTag("onboarding_days_5").performClick()
        composeRule.onNodeWithTag("onboarding_weekly_goal").assertTextContains("Completar 5 entrenamientos")

        composeRule.onNodeWithTag("onboarding_place_Casa").performClick()
        composeRule.onNodeWithTag("onboarding_equipment").assertTextContains("Peso corporal y equipo básico")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
