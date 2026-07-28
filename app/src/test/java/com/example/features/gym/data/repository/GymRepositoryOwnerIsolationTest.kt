package com.example.features.gym.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.WorkoutLogEntity
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
class GymRepositoryOwnerIsolationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: PreferencesManager
    private lateinit var repository: GymRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = PreferencesManager(context)
        repository = GymRepositoryImpl(database.workoutDao(), database.userDao(), preferences)
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun crossOwnerWorkoutIsRejectedWithoutCopyingSensitiveFields() = runTest {
        startLocal("a@example.test", "123456")
        repository.addWorkoutLog(
            WorkoutLogEntity(
                routineName = "private-a",
                durationMinutes = 42,
                caloriesBurned = 321,
                exercisesText = "private-exercise-a"
            )
        )
        val rowA = repository.getWorkoutLogsFlow().first().single()

        preferences.endSession()
        startLocal("b@example.test", "654321")
        var thrown: Throwable? = null
        try {
            repository.addWorkoutLog(rowA)
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue("Cross-owner writes must fail closed", thrown is SecurityException)
        assertTrue(repository.getWorkoutLogsFlow().first().isEmpty())

        preferences.endSession()
        startLocal("a@example.test", "123456")
        val restoredA = repository.getWorkoutLogsFlow().first()
        assertEquals(1, restoredA.size)
        assertEquals("private-a", restoredA.single().routineName)
    }

    private fun startLocal(email: String, pin: String) {
        val method = requireNotNull(preferences.javaClass.methods.singleOrNull { candidate ->
            candidate.name == "startLocalAccountSession" && candidate.parameterCount == 2
        })
        try {
            method.invoke(preferences, email, pin)
        } catch (wrapped: java.lang.reflect.InvocationTargetException) {
            throw requireNotNull(wrapped.targetException)
        }
    }
}
