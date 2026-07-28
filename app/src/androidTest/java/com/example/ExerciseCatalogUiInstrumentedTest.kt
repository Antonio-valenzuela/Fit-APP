package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.UserDiscipline
import com.example.data.model.UserEntity
import com.example.data.model.getTrainingPlace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogUiInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var ownerId: String

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun featureFlagDisabledKeepsCatalogEntryHidden() {
        launch(flagEnabled = false)
        openWorkout()
        scrollToTag("workout_equipment_Gym")
        composeRule.onAllNodesWithTag("open_exercise_catalog").assertCountEquals(0)
    }

    @Test
    fun featureFlagEnabledSupportsSearchFiltersDetailAndReopen() {
        launch(flagEnabled = true)
        openWorkout()
        scrollToTag("open_exercise_catalog")
        composeRule.onNodeWithTag("open_exercise_catalog").performClick()
        waitForTag("exercise_catalog_screen")

        listOf(
            "catalog_filter_body_region",
            "catalog_filter_muscle",
            "catalog_filter_primary_equipment",
            "catalog_clear_filters"
        ).forEach { tag ->
            waitForTag(tag)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        selectFilter("catalog_filter_body_region", "catalog_body_region_chest")
        selectFilter("catalog_filter_muscle", "catalog_muscle_chest")
        selectFilter("catalog_filter_primary_equipment", "catalog_primary_equipment_barbell")

        searchFor("press de banca")
        scrollCatalogToTag("catalog_item_bench_press")
        waitForTag("catalog_item_bench_press")

        composeRule.onNodeWithTag("catalog_clear_filters").performClick()
        composeRule.onNodeWithTag("catalog_location_any").assertIsSelected()

        composeRule.onNodeWithTag("catalog_location_home").performClick()
        searchFor("flexiones")
        scrollCatalogToTag("catalog_item_push_up")
        waitForTag("catalog_item_push_up")
        composeRule.onNodeWithTag("catalog_item_push_up").performClick()
        waitForTag("exercise_catalog_detail_screen")
        waitForTag("exercise_catalog_detail_name")
        composeRule.onNodeWithTag("exercise_catalog_detail_name").assertIsDisplayed()
        waitForTag("exercise_catalog_detail_body_region")
        composeRule.onNodeWithTag("exercise_catalog_detail_body_region").assertIsDisplayed()
        waitForTag("exercise_catalog_detail_placeholder")
        composeRule.onNodeWithTag("exercise_catalog_detail_placeholder").assertIsDisplayed()

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
        openWorkout()
        scrollToTag("open_exercise_catalog")
        composeRule.onNodeWithTag("open_exercise_catalog").assertIsDisplayed()
    }

    @Test
    fun coldStartAppliesPersistedHomeAfterProfileLoadsAndReopen() {
        launch(flagEnabled = true, trainingPlace = "Casa")
        openWorkout()
        scrollToTag("open_exercise_catalog")
        composeRule.onNodeWithTag("open_exercise_catalog").performClick()
        waitForTag("exercise_catalog_screen")
        composeRule.onNodeWithTag("catalog_location_home").assertIsSelected()

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
        openWorkout()
        scrollToTag("open_exercise_catalog")
        composeRule.onNodeWithTag("open_exercise_catalog").performClick()
        waitForTag("exercise_catalog_screen")
        composeRule.onNodeWithTag("catalog_location_home").assertIsSelected()
    }

    @Test
    fun settingsTrainingPlaceSavePersistsUserAndCatalogSelectionAfterReopen() {
        launch(flagEnabled = true, trainingPlace = "Gimnasio")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = runBlocking { AppDatabase.getInstance(context).userDao().getUser(ownerId)!! }

        composeRule.onNodeWithTag("bottom_nav_profile").performClick()
        waitForTag("profile_screen")
        scrollToTag("open_settings_button")
        composeRule.onNodeWithTag("open_settings_button").performClick()
        waitForTag("settings_screen")
        composeRule.onNodeWithTag("settings_edit_profile").performClick()
        waitForTag("edit_profile_dialog")
        composeRule.onNodeWithTag("edit_profile_place_casa").performClick()
        composeRule.onNodeWithTag("edit_profile_save").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("edit_profile_dialog").fetchSemanticsNodes().isEmpty()
        }

        val saved = runBlocking { AppDatabase.getInstance(context).userDao().getUser(ownerId)!! }
        assertEquals(before.uid, saved.uid)
        assertEquals(before.email, saved.email)
        assertEquals(before.isPremium, saved.isPremium)
        assertEquals(before.disclaimerAccepted, saved.disclaimerAccepted)
        assertEquals(before.trainingDiscipline, saved.trainingDiscipline)
        assertEquals(before.targetCalories, saved.targetCalories)
        assertEquals(before.targetProteinG, saved.targetProteinG)
        assertEquals(before.targetCarbsG, saved.targetCarbsG)
        assertEquals(before.targetFatG, saved.targetFatG)
        assertEquals("Casa", saved.getTrainingPlace())

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
        val reopened = runBlocking { AppDatabase.getInstance(context).userDao().getUser(ownerId)!! }
        assertEquals("Casa", reopened.getTrainingPlace())
        assertEquals(saved.uid, reopened.uid)
        assertEquals(saved.email, reopened.email)
        openWorkout()
        scrollToTag("open_exercise_catalog")
        composeRule.onNodeWithTag("open_exercise_catalog").performClick()
        waitForTag("exercise_catalog_screen")
        composeRule.onNodeWithTag("catalog_location_home").assertIsSelected()
    }

    private fun launch(flagEnabled: Boolean, trainingPlace: String = "Gimnasio") {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ownerId = provisionLocalTestSession(context, "qa.exercise-catalog@example.com")
        context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("feature_workout_enabled", true)
            .putBoolean("feature_exercise_catalog_enabled", flagEnabled)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        runBlocking {
            val dao = AppDatabase.getInstance(context).userDao()
            val current = dao.getUser(ownerId)
            val base = current ?: UserEntity(uid = ownerId, name = "QA Catalog", email = "qa.catalog@example.com")
            dao.insertUser(
                base.copy(
                    trainingDiscipline = UserDiscipline.GYM.name,
                    goal = "Mantener peso | Nivel: Intermedio | Lugar: $trainingPlace",
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

    private fun openWorkout() {
        composeRule.onNodeWithTag("bottom_nav_workout").performClick()
        waitForTag("workout_screen")
    }

    private fun searchFor(query: String) {
        composeRule.onNodeWithTag("exercise_catalog_search").performTextClearance()
        composeRule.onNodeWithTag("exercise_catalog_search").performTextInput(query)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("exercise_catalog_loading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag(tag))
    }

    private fun scrollCatalogToTag(tag: String) {
        composeRule.onNodeWithTag("exercise_catalog_list")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun selectFilter(rowTag: String, optionTag: String) {
        composeRule.onNodeWithTag(rowTag).performScrollToNode(hasTestTag(optionTag))
        composeRule.onNodeWithTag(optionTag).performClick()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
