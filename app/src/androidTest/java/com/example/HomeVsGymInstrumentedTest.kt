package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class HomeVsGymInstrumentedTest {
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
            .putBoolean("feature_workout_enabled", true)
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
    fun homeProfileSelectsHomeAndShowsHomeRoutine() {
        launchForPlace("Casa", level = "Principiante")
        openWorkout()
        waitForSelectedEquipment("Casa")
        scrollToRoutine("mobility_recovery")
        reopenWorkoutAndVerify("Casa")
    }

    @Test
    fun gymProfileSelectsGymAndShowsGymRoutineEvenAtThirtyMinutes() {
        launchForPlace("Gimnasio", level = "Intermedio")
        openWorkout()
        waitForSelectedEquipment("Gym")
        scrollToRoutine("hamstrings_glutes")
        reopenWorkoutAndVerify("Gym")
    }

    private fun launchForPlace(place: String, level: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownerId = provisionLocalTestSession(context, "qa.home-vs-gym@example.com")
        runBlocking {
            val dao = AppDatabase.getInstance(context).userDao()
            val current = dao.getUser(ownerId)
            val base = current ?: UserEntity(uid = ownerId, name = "QA Huawei", email = "qa.huawei@example.com")
            val baseGoal = base.goal.substringBefore("|").trim().ifBlank { "Mantener peso" }
            dao.insertUser(
                base.copy(
                    trainingDiscipline = UserDiscipline.GYM.name,
                    goal = "$baseGoal | Nivel: $level | Lugar: $place",
                    disclaimerAccepted = true
                )
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
    }

    private fun reopenWorkoutAndVerify(equipment: String) {
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
        openWorkout()
        waitForSelectedEquipment(equipment)
    }

    private fun openWorkout() {
        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        waitForTag("workout_screen")
    }

    private fun waitForSelectedEquipment(equipment: String) {
        val tag = "workout_equipment_${equipment.replace(' ', '_')}"
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsSelected()
                true
            }.getOrDefault(false)
        }
    }

    private fun scrollToRoutine(id: String) {
        val tag = "routine_card_$id"
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
