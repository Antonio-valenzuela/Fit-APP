package com.example

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.AchievementEntity
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.DailyActivityEntity
import com.example.data.model.DailyMissionEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.StreakEntity
import com.example.data.model.UserDiscipline
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersistenceLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun wakeDevice() {
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun a_seedAndVerifyRecreationAndBackground() {
        seedDurableSentinels()
        launchAndWaitForDashboard()
        verifyDurableSentinels()

        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        waitForTag("nutrition_screen")
        scenario.recreate()
        waitForTag("nutrition_screen")

        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("nutrition_screen")
        verifyDurableSentinels()
    }

    @Test
    fun b_verifyDurableSentinelsAfterExternalRestart() {
        verifyDurableSentinels()
        launchAndWaitForDashboard()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_workout").assertIsDisplayed()
        verifyDurableSentinels()
    }

    private fun seedDurableSentinels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownerId = provisionLocalTestSession(context, "qa.persistence@example.com")
        PreferencesManager(context).setThemeSelector("dark")
        val db = AppDatabase.getInstance(context)
        runBlocking {
            db.userDao().insertUser(
                UserEntity(
                    uid = ownerId,
                    name = "QA Persistencia Huawei",
                    email = "qa.persistence@example.com",
                    goal = "Mantener peso | Lugar: Casa",
                    disclaimerAccepted = true,
                    trainingDiscipline = UserDiscipline.GYM.name
                )
            )
            db.workoutDao().insertWorkoutLog(
                WorkoutLogEntity(SENTINEL_INT, "QA Rutina persistente", 31, 217, "Sentadilla 3x10", SENTINEL_TIME, ownerId)
            )
            db.foodEntryDao().insertFoodEntry(
                FoodEntryEntity(
                    id = SENTINEL_LONG,
                    dateKey = SENTINEL_DATE,
                    mealType = "Comida",
                    foodName = "QA Comida persistente",
                    portionLabel = "1 plato",
                    calories = 432,
                    proteinGrams = 28f,
                    source = "QA_PERSISTENCE",
                    createdAt = SENTINEL_TIME,
                    updatedAt = SENTINEL_TIME,
                    ownerId = ownerId
                )
            )
            db.foodItemDao().insertFoodItem(
                FoodItemEntity(
                    id = SENTINEL_LONG,
                    name = "QA Favorito persistente",
                    barcode = "QA-PERSIST-900001",
                    caloriesPerServing = 111,
                    isFavorite = true,
                    createdAt = SENTINEL_TIME,
                    ownerId = ownerId
                )
            )
            db.healthDao().insertHealthLog(
                HealthLogEntity(
                    id = SENTINEL_INT,
                    type = "WEIGHT",
                    value = 72.5f,
                    unit = "kg",
                    context = "QA_PERSISTENCE",
                    dateKey = SENTINEL_DATE,
                    createdAt = SENTINEL_TIME,
                    updatedAt = SENTINEL_TIME,
                    weightKg = 72.5f,
                    timestamp = SENTINEL_TIME,
                    ownerId = ownerId
                )
            )
            db.activitySessionDao().insertActivitySession(
                ActivitySessionEntity(
                    id = SENTINEL_LONG,
                    discipline = UserDiscipline.WALKING.name,
                    title = "QA Caminata persistente",
                    distanceKm = 1.25f,
                    durationMinutes = 18,
                    durationSeconds = 1_080,
                    source = "QA_PERSISTENCE",
                    timestamp = SENTINEL_TIME,
                    ownerId = ownerId
                )
            )
            db.dailyActivityDao().insertActivity(
                DailyActivityEntity(
                    id = SENTINEL_LONG,
                    userId = ownerId,
                    date = SENTINEL_DATE,
                    hasFoodLog = true,
                    hasWaterLog = true,
                    hasWorkoutLog = true,
                    hasCheckIn = true,
                    isActiveDay = true,
                    createdAt = SENTINEL_TIME,
                    updatedAt = SENTINEL_TIME
                )
            )
            db.streakDao().insertStreak(StreakEntity(ownerId, 7, 9, SENTINEL_DATE, SENTINEL_TIME))
            db.dailyMissionDao().insertMissions(
                listOf(DailyMissionEntity(SENTINEL_LONG, ownerId, SENTINEL_DATE, "qa_mission", "QA Misión", "Persistir", true, SENTINEL_TIME))
            )
            db.achievementDao().insertAchievement(
                AchievementEntity(SENTINEL_LONG, ownerId, "qa_achievement", "QA Logro", "Persistir", SENTINEL_TIME)
            )
        }
    }

    private fun verifyDurableSentinels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferencesManager(context)
        val ownerId = activeTestOwner(context)
        assertTrue(preferences.isLoggedIn())
        assertTrue(preferences.isOnboardingCompleted())
        assertEquals("dark", preferences.getThemeSelector())
        val db = AppDatabase.getInstance(context)
        runBlocking {
            val user = db.userDao().getUser(ownerId)
            assertEquals("QA Persistencia Huawei", user?.name)
            assertEquals("Casa", user?.goal?.substringAfter("Lugar: "))
            assertTrue(db.workoutDao().getWorkoutLogsFlow(ownerId).first().any { it.id == SENTINEL_INT })
            assertTrue(db.foodEntryDao().getFoodEntriesFlow(ownerId).first().any { it.id == SENTINEL_LONG })
            assertTrue(db.foodItemDao().getFavoriteFoodItemsFlow(ownerId).first().any { it.id == SENTINEL_LONG })
            assertEquals(SENTINEL_INT, db.healthDao().getHealthLogForDateTypeContext(ownerId, SENTINEL_DATE, "WEIGHT", "QA_PERSISTENCE")?.id)
            assertTrue(db.activitySessionDao().getActivitySessionsFlow(ownerId).first().any { it.id == SENTINEL_LONG })
            assertEquals(SENTINEL_LONG, db.dailyActivityDao().getActivityForDay(ownerId, SENTINEL_DATE)?.id)
            assertEquals(7, db.streakDao().getStreak(ownerId)?.currentStreak)
            assertTrue(db.dailyMissionDao().getMissionsForDay(ownerId, SENTINEL_DATE).any { it.id == SENTINEL_LONG })
            assertTrue(db.achievementDao().getAchievements(ownerId).any { it.id == SENTINEL_LONG })
        }
    }

    private fun launchAndWaitForDashboard() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val SENTINEL_INT = 900_001
        private const val SENTINEL_LONG = 900_001L
        private const val SENTINEL_TIME = 1_800_000_000_000L
        private const val SENTINEL_DATE = "2027-01-15"
    }
}
