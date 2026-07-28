package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteWorkoutInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var ownerId: String

    @Before
    fun launchGymSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ownerId = provisionLocalTestSession(context, "qa.complete-workout@example.com")
        context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("feature_workout_enabled", true)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        runBlocking {
            val dao = AppDatabase.getInstance(context).userDao()
            val current = dao.getUser(ownerId)
            val base = current ?: UserEntity(uid = ownerId, name = "QA Huawei", email = "qa.huawei@example.com")
            dao.insertUser(
                base.copy(
                    trainingDiscipline = UserDiscipline.GYM.name,
                    goal = "Mantener peso | Nivel: Intermedio | Lugar: Gimnasio",
                    disclaimerAccepted = true
                )
            )
        }
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
    fun completesSavesShowsHistoryAndRejectsDuplicateSave() {
        val db = AppDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        val baselineIds = runBlocking { db.workoutDao().getWorkoutLogsFlow(ownerId).first().map { it.id }.toSet() }

        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        waitForTag("workout_screen")
        scrollToTag("start_routine_hamstrings_glutes")
        composeRule.onNodeWithTag("start_routine_hamstrings_glutes").performClick()
        waitForTag("active_workout_screen")

        repeat(14) {
            composeRule.onNodeWithTag("complete_set_btn").performClick()
        }

        val saveNode = composeRule.onNodeWithTag("save_workout_btn").fetchSemanticsNode()
        val saveAction = saveNode.config[SemanticsActions.OnClick].action
            ?: error("El botón de guardar no expone una acción semántica")
        composeRule.runOnIdle {
            saveAction.invoke()
            saveAction.invoke()
        }

        waitForTag("workout_screen")
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runBlocking {
                db.workoutDao().getWorkoutLogsFlow(ownerId).first().size == baselineIds.size + 1
            }
        }
        val newLogs = runBlocking {
            db.workoutDao().getWorkoutLogsFlow(ownerId).first().filter { it.id !in baselineIds }
        }
        check(newLogs.size == 1) { "Se crearon ${newLogs.size} registros nuevos en vez de uno" }

        val historyTag = "workout_history_${newLogs.single().id}"
        scrollToTag(historyTag)
        composeRule.onNodeWithTag(historyTag).assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_dashboard").performClick()
        waitForTag("dashboard_screen")
        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        waitForTag("workout_screen")
        scrollToTag(historyTag)
        composeRule.onNodeWithTag(historyTag).assertIsDisplayed()
    }

    private fun scrollToTag(tag: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
