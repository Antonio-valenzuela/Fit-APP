package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ActivitySessionEntity
import com.example.data.model.FoodEntryEntity
import com.example.data.model.FoodItemEntity
import com.example.data.model.FoodLogEntity
import com.example.data.model.HealthLogEntity
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FitPlanRepositoryOwnerIsolationTest {
    private lateinit var database: AppDatabase
    private lateinit var activeOwnerId: MutableStateFlow<String?>
    private lateinit var repository: FitPlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activeOwnerId = MutableStateFlow(OWNER_A)
        repository = FitPlanRepository(
            database.userDao(),
            database.foodDao(),
            database.foodEntryDao(),
            database.foodItemDao(),
            database.workoutDao(),
            database.activitySessionDao(),
            database.healthDao(),
            activeOwnerId,
            database.accountDataDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun switchingFromAToBRejectsARecordAndARecoversItsRows() = runTest {
        repository.addFoodLog(
            FoodLogEntity(
                mealName = "private-a",
                mealType = "Comida",
                calories = 500,
                proteinG = 30f,
                carbsG = 50f,
                fatG = 15f
            )
        )
        val rowA = repository.allFoodLogs.first().single()

        activeOwnerId.value = null
        assertTrue(repository.allFoodLogs.first().isEmpty())

        activeOwnerId.value = OWNER_B
        assertTrue(repository.allFoodLogs.first().isEmpty())
        repository.deleteFoodLog(rowA)
        expectSecurityException {
            repository.addFoodLog(rowA.copy(mealName = "attempted-overwrite-by-b"))
        }
        assertTrue(repository.allFoodLogs.first().isEmpty())

        activeOwnerId.value = OWNER_A
        val restoredA = repository.allFoodLogs.first()
        assertEquals(1, restoredA.size)
        assertEquals("private-a", restoredA.single().mealName)
        assertEquals(OWNER_A, restoredA.single().ownerId)
    }

    @Test
    fun everyExistingOwnerScopedEntityIsRejectedAcrossSessions() = runTest {
        activeOwnerId.value = OWNER_B

        expectSecurityException {
            repository.saveUser(UserEntity(uid = OWNER_A, name = "A", email = "a@example.test"))
        }
        expectSecurityException {
            repository.addFoodEntry(
                FoodEntryEntity(
                    id = 11,
                    dateKey = "2026-07-19",
                    mealType = "Comida",
                    foodName = "private-a",
                    portionLabel = "1",
                    ownerId = OWNER_A
                )
            )
        }
        expectSecurityException {
            repository.saveFoodItem(
                FoodItemEntity(id = 12, name = "private-a", ownerId = OWNER_A)
            )
        }
        expectSecurityException {
            repository.addWorkoutLog(
                WorkoutLogEntity(
                    id = 13,
                    routineName = "private-a",
                    durationMinutes = 30,
                    caloriesBurned = 100,
                    exercisesText = "private-a",
                    ownerId = OWNER_A
                )
            )
        }
        expectSecurityException {
            repository.addActivitySession(
                ActivitySessionEntity(
                    id = 14,
                    discipline = "RUNNING",
                    title = "private-a",
                    ownerId = OWNER_A
                )
            )
        }
        expectSecurityException {
            repository.addHealthLog(
                HealthLogEntity(id = 15, type = "weight", value = 70f, ownerId = OWNER_A)
            )
        }
        expectSecurityException {
            repository.updateHealthLog(
                HealthLogEntity(id = 16, type = "weight", value = 70f, ownerId = OWNER_A)
            )
        }

        assertTrue(repository.allFoodEntries.first().isEmpty())
        assertTrue(repository.allFoodItems.first().isEmpty())
        assertTrue(repository.allWorkoutLogs.first().isEmpty())
        assertTrue(repository.allActivitySessions.first().isEmpty())
        assertTrue(repository.allHealthLogs.first().isEmpty())
    }

    private suspend fun expectSecurityException(block: suspend () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(
            "Cross-owner writes must fail closed with SecurityException, got $thrown",
            thrown is SecurityException
        )
    }

    private companion object {
        const val OWNER_A = "local_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OWNER_B = "local_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
