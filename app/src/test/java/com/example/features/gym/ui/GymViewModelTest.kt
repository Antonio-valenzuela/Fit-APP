package com.example.features.gym.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.gym.domain.repository.GymRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GymViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var fakeRepository: FakeGymRepository
    private lateinit var viewModel: GymViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesManager(context)
        fakeRepository = FakeGymRepository()
        viewModel = GymViewModel(fakeRepository, prefs, db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertEquals(emptyList<WorkoutLogEntity>(), state.workouts)
        }
    }

    @Test
    fun testWorkoutAndUserEmissions() = runTest(testDispatcher) {
        viewModel.state.test {
            var state = awaitItem()
            assertTrue(state.isLoading)

            val testUser = UserEntity(uid = "local_user", name = "Gym Lover", email = "gym@fitplan.mx")
            val testWorkout = WorkoutLogEntity(id = 1, routineName = "Pierna", durationMinutes = 30, caloriesBurned = 200, exercisesText = "Squats")

            fakeRepository.emitUser(testUser)
            fakeRepository.emitWorkouts(listOf(testWorkout))

            state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("Gym Lover", state.user?.name)
            assertEquals(1, state.workouts.size)
            assertEquals("Pierna", state.workouts[0].routineName)
            assertTrue(state.isPremiumActive)
        }
    }

    @Test
    fun testRegisterWorkout() = runTest(testDispatcher) {
        viewModel.registerWorkout("Pecho y Triceps", 45, 350, "Bench Press: 3x10")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Pecho y Triceps", fakeRepository.lastAddedWorkout?.routineName)
        assertEquals(45, fakeRepository.lastAddedWorkout?.durationMinutes)
    }

    @Test
    fun testRemoveWorkout() = runTest(testDispatcher) {
        val testWorkout = WorkoutLogEntity(id = 1, routineName = "Pierna", durationMinutes = 30, caloriesBurned = 200, exercisesText = "Squats")
        viewModel.removeWorkoutLog(testWorkout)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testWorkout, fakeRepository.lastDeletedWorkout)
    }

    private class FakeGymRepository : GymRepository {
        private val workoutFlow = MutableStateFlow<List<WorkoutLogEntity>>(emptyList())
        private val userFlow = MutableStateFlow<UserEntity?>(null)

        var lastAddedWorkout: WorkoutLogEntity? = null
        var lastDeletedWorkout: WorkoutLogEntity? = null

        fun emitWorkouts(workouts: List<WorkoutLogEntity>) {
            workoutFlow.value = workouts
        }

        fun emitUser(user: UserEntity?) {
            userFlow.value = user
        }

        override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> = workoutFlow
        override fun getUserFlow(): Flow<UserEntity?> = userFlow

        override suspend fun addWorkoutLog(log: WorkoutLogEntity) {
            lastAddedWorkout = log
        }

        override suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
            lastDeletedWorkout = log
        }
    }
}
