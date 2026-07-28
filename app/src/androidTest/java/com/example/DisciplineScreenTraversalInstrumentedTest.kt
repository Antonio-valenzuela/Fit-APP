package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.UserDiscipline
import com.example.data.model.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisciplineScreenTraversalInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun prepareSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("onboarding_completed", true)
            .putBoolean("feature_health_enabled", true)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun runningOpensTrackerAndHistory() {
        launchFor(UserDiscipline.RUNNING)
        openTrackerAndHistory("active_run", "active_activity_running_screen", "running_history_screen")
    }

    @Test
    fun cyclingOpensTrackerAndHistory() {
        launchFor(UserDiscipline.CYCLING)
        openTrackerAndHistory("active_ride", "active_activity_cycling_screen", "cycling_history_screen")
    }

    @Test
    fun walkingOpensTrackerAndHistory() {
        launchFor(UserDiscipline.WALKING)
        openTrackerAndHistory("active_walk", "active_activity_walking_screen", "walking_history_screen")
    }

    @Test
    fun wellnessOpensDiaryAndDailyCheckIn() {
        launchFor(UserDiscipline.WELLNESS)
        composeRule.onNodeWithTag("bottom_nav_wellness_diary").performClick()
        waitForTag("health_screen")
        composeRule.onNodeWithTag("bottom_nav_daily_check_in").performClick()
        waitForTag("daily_check_in_screen")
        composeRule.onNodeWithText("Omitir por ahora").performClick()
        waitForTag("dashboard_screen")
    }

    @Test
    fun hybridOpensDailyPlanCardioAndWellness() {
        launchFor(UserDiscipline.HYBRID)
        composeRule.onNodeWithTag("bottom_nav_daily_plan").performClick()
        waitForTag("daily_plan_screen")
        composeRule.onNodeWithContentDescription("Regresar").performClick()
        waitForTag("dashboard_screen")

        composeRule.onNodeWithTag("bottom_nav_active_run").performClick()
        waitForTag("active_activity_running_screen")
        composeRule.onNodeWithContentDescription("Regresar").performClick()
        waitForTag("dashboard_screen")

        composeRule.onNodeWithTag("bottom_nav_wellness_diary").performClick()
        waitForTag("health_screen")
    }

    private fun openTrackerAndHistory(bottomRoute: String, trackerTag: String, historyTag: String) {
        composeRule.onNodeWithTag("bottom_nav_$bottomRoute").performClick()
        waitForTag(trackerTag)
        composeRule.onNodeWithText("Ver historial").performClick()
        waitForTag(historyTag)
        composeRule.onNodeWithContentDescription("Regresar").performClick()
        waitForTag("dashboard_screen")
    }

    private fun launchFor(discipline: UserDiscipline) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownerId = provisionLocalTestSession(context, "qa.discipline@example.com")
        runBlocking {
            val dao = AppDatabase.getInstance(context).userDao()
            val current = dao.getUser(ownerId)
            dao.insertUser(
                (current ?: UserEntity(uid = ownerId, name = "QA Huawei", email = "qa.huawei@example.com"))
                    .copy(trainingDiscipline = discipline.name, disclaimerAccepted = true)
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }
}
