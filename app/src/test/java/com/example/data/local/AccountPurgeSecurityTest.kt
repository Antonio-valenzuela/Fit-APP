package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AchievementEntity
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.DailyActivityEntity
import com.example.data.model.DailyMissionEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.StreakEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.data.repository.FitPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AccountPurgeSecurityTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPreferences().edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        rawPreferences().edit().clear().commit()
    }

    @Test
    fun purgeDeletesEveryOwnerRowAndCredentialBeforeTheIdentityCanReopen() = runTest {
        val preferences = PreferencesManager(context)
        val ownerId = preferences.startLocalAccountSession(EMAIL, ORIGINAL_PIN)
        preferences.setOnboardingCompleted(true)
        val activeOwner = MutableStateFlow<String?>(ownerId)
        val repository = FitPlanRepository(
            database.userDao(),
            database.foodDao(),
            database.foodEntryDao(),
            database.foodItemDao(),
            database.workoutDao(),
            database.activitySessionDao(),
            database.healthDao(),
            activeOwner,
            database.accountDataDao()
        )

        seedEveryOwnerTable(repository, ownerId)

        repository.wipeUserData()
        preferences.clearAllPreferences()
        activeOwner.value = null

        val afterPurge = rawPreferences().all
        assertFalse(afterPurge.keys.any { it.startsWith("identity_binding_v2_") })
        assertFalse(afterPurge.keys.any { it.startsWith("local_pin_salt_v1_") })
        assertFalse(afterPurge.keys.any { it.startsWith("local_pin_hash_v1_") })

        val reopenedOwner = preferences.startLocalAccountSession(EMAIL, REPLACEMENT_PIN)
        activeOwner.value = reopenedOwner
        assertEquals(ownerId, reopenedOwner)
        assertFalse(preferences.isOnboardingCompleted())
        assertEveryOwnerTableEmpty(reopenedOwner)
    }

    private suspend fun seedEveryOwnerTable(repository: FitPlanRepository, ownerId: String) {
        repository.saveUser(UserEntity(uid = ownerId, name = "Private", email = EMAIL))
        repository.addFoodLog(
            FoodLogEntity(
                mealName = "Private",
                mealType = "Comida",
                calories = 1,
                proteinG = 1f,
                carbsG = 1f,
                fatG = 1f
            )
        )
        repository.addFoodEntry(
            FoodEntryEntity(
                dateKey = DATE,
                mealType = "Comida",
                foodName = "Private",
                portionLabel = "1"
            )
        )
        repository.saveFoodItem(FoodItemEntity(name = "Private"))
        repository.addWorkoutLog(
            WorkoutLogEntity(
                routineName = "Private",
                durationMinutes = 1,
                caloriesBurned = 1,
                exercisesText = "Private"
            )
        )
        repository.addActivitySession(ActivitySessionEntity(discipline = "RUNNING", title = "Private"))
        repository.addHealthLog(HealthLogEntity(type = "weight", value = 70f))
        database.dailyActivityDao().insertActivity(
            DailyActivityEntity(userId = ownerId, date = DATE, createdAt = 1, updatedAt = 1)
        )
        database.streakDao().insertStreak(StreakEntity(userId = ownerId, currentStreak = 2, updatedAt = 1))
        database.dailyMissionDao().insertMissions(
            listOf(
                DailyMissionEntity(
                    userId = ownerId,
                    date = DATE,
                    missionKey = "private",
                    title = "Private",
                    description = "Private"
                )
            )
        )
        database.achievementDao().insertAchievement(
            AchievementEntity(
                userId = ownerId,
                achievementKey = "private",
                title = "Private",
                description = "Private",
                unlockedAt = 1
            )
        )
    }

    private suspend fun assertEveryOwnerTableEmpty(ownerId: String) {
        assertNull(database.userDao().getUser(ownerId))
        assertTrue(database.foodDao().getFoodLogsFlow(ownerId).first().isEmpty())
        assertTrue(database.foodEntryDao().getFoodEntriesFlow(ownerId).first().isEmpty())
        assertTrue(database.foodItemDao().getFoodItemsFlow(ownerId).first().isEmpty())
        assertTrue(database.workoutDao().getWorkoutLogsFlow(ownerId).first().isEmpty())
        assertTrue(database.activitySessionDao().getActivitySessionsFlow(ownerId).first().isEmpty())
        assertTrue(database.healthDao().getHealthLogsFlow(ownerId).first().isEmpty())
        assertTrue(database.dailyActivityDao().getAllActivitiesFlow(ownerId).first().isEmpty())
        assertNull(database.streakDao().getStreak(ownerId))
        assertTrue(database.dailyMissionDao().getMissionsForDay(ownerId, DATE).isEmpty())
        assertTrue(database.achievementDao().getAchievements(ownerId).isEmpty())
    }

    private fun rawPreferences() = context.getSharedPreferences(
        "fitplan_mx_preferences",
        Context.MODE_PRIVATE
    )

    private companion object {
        const val EMAIL = "private@example.test"
        const val ORIGINAL_PIN = "123456"
        const val REPLACEMENT_PIN = "654321"
        const val DATE = "2026-07-21"
    }
}
