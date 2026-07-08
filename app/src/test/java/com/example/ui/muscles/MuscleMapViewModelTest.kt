package com.example.ui.muscles

import app.cash.turbine.test
import com.example.data.model.BodyModel
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.musclemap.domain.repository.MuscleMapRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleMapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMuscleMapRepository
    private lateinit var viewModel: MuscleMapViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeMuscleMapRepository()
        viewModel = MuscleMapViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.isLoading)
            assertFalse(initialState.hasWorkoutData)
            assertEquals(BodyModel.MALE, initialState.selectedBodyModel)
        }
    }

    @Test
    fun testUserGenderDeterminesBodyModel() = runTest(testDispatcher) {
        viewModel.state.test {
            // Initial state
            var state = awaitItem()
            assertTrue(state.isLoading)

            // Emit female user
            fakeRepository.emitUser(UserEntity(uid = "local_user", name = "Test User", email = "test@fitplan.mx", gender = "Femenino"))
            state = awaitItem()
            assertEquals(BodyModel.FEMALE, state.selectedBodyModel)

            // Emit male user
            fakeRepository.emitUser(UserEntity(uid = "local_user", name = "Test User", email = "test@fitplan.mx", gender = "Masculino"))
            state = awaitItem()
            assertEquals(BodyModel.MALE, state.selectedBodyModel)
        }
    }

    @Test
    fun testWorkoutsProcessedIntoHeatmap() = runTest(testDispatcher) {
        viewModel.state.test {
            var state = awaitItem()
            assertTrue(state.isLoading)

            // Emit workout log
            fakeRepository.emitWorkoutLogs(listOf(
                WorkoutLogEntity(
                    id = 1,
                    routineName = "Rutina Pecho",
                    durationMinutes = 45,
                    caloriesBurned = 300,
                    exercisesText = "Press de banca: 3 series x 10 reps, Peso: 60, RPE: 8",
                    timestamp = System.currentTimeMillis()
                )
            ))
            state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.hasWorkoutData)
            assertNotNull(state.recommendation)
        }
    }

    @Test
    fun testFlowErrorHandling() = runTest(testDispatcher) {
        val errorRepo = object : MuscleMapRepository {
            override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> = flow {
                throw RuntimeException("Database error")
            }
            override fun getUserFlow(): Flow<UserEntity?> = MutableStateFlow(null)
        }
        val errorViewModel = MuscleMapViewModel(errorRepo)
        errorViewModel.state.test {
            var state = awaitItem()
            assertTrue(state.isLoading)
            state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("Database error"))
        }
    }

    private class FakeMuscleMapRepository : MuscleMapRepository {
        private val workoutLogsFlow = MutableStateFlow<List<WorkoutLogEntity>>(emptyList())
        private val userFlow = MutableStateFlow<UserEntity?>(null)

        fun emitWorkoutLogs(logs: List<WorkoutLogEntity>) {
            workoutLogsFlow.value = logs
        }

        fun emitUser(user: UserEntity?) {
            userFlow.value = user
        }

        override fun getWorkoutLogsFlow(): Flow<List<WorkoutLogEntity>> = workoutLogsFlow
        override fun getUserFlow(): Flow<UserEntity?> = userFlow
    }
}
