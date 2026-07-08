package com.example.features.gym.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.local.AppDatabase
import com.example.data.model.PremiumAccessPolicy
import com.example.data.model.UserEntity
import com.example.data.model.WorkoutLogEntity
import com.example.features.gym.domain.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.data.analytics.ProductAnalytics
import com.example.data.model.DailyActivityEntity
import com.example.data.repository.LocalDateUtils
import com.example.data.repository.StreakRepository
import com.example.data.repository.DailyActivityRepository
import com.example.data.repository.StreakManager
import com.example.data.repository.DailyMissionRepository
import com.example.data.repository.AchievementRepository
import com.example.data.repository.AchievementManager
import com.example.data.repository.DailyMissionManager
import com.example.data.model.WellnessEntryType
import com.example.data.model.WellnessEntryRules
import com.example.data.model.HealthLogEntity
import kotlinx.coroutines.flow.first

data class GymUiState(
    val workouts: List<WorkoutLogEntity> = emptyList(),
    val user: UserEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPremiumActive: Boolean = false
)

@HiltViewModel
class GymViewModel @Inject constructor(
    private val repository: GymRepository,
    private val prefs: PreferencesManager,
    private val db: AppDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(GymUiState())
    val state: StateFlow<GymUiState> = _state.asStateFlow()

    init {
        observeGymData()
    }

    private fun observeGymData() {
        viewModelScope.launch {
            combine(
                repository.getWorkoutLogsFlow(),
                repository.getUserFlow()
            ) { workouts, user ->
                GymUiState(
                    workouts = workouts,
                    user = user,
                    isLoading = false,
                    error = null,
                    isPremiumActive = PremiumAccessPolicy.isPremiumActive(
                        storedPremiumUnlocked = prefs.isPremiumUnlocked(),
                        isDebugBuild = BuildConfig.DEBUG
                    )
                )
            }
            .catch { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos de entrenamiento: ${e.message}"
                )
            }
            .collect { nextState ->
                _state.value = nextState
            }
        }
    }

    fun registerWorkout(name: String, duration: Int, calories: Int, repsExercises: String) {
        viewModelScope.launch {
            try {
                repository.addWorkoutLog(
                    WorkoutLogEntity(
                        routineName = name,
                        durationMinutes = duration,
                        caloriesBurned = calories,
                        exercisesText = repsExercises
                    )
                )
                markWorkoutCompletedFromTrainingSession()
                triggerGamificationHook("log_workout")
                ProductAnalytics.track("workout_completed", mapOf("routine" to name, "duration" to duration.toString()))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error al registrar entrenamiento: ${e.message}")
            }
        }
    }

    fun removeWorkoutLog(log: WorkoutLogEntity) {
        viewModelScope.launch {
            try {
                repository.deleteWorkoutLog(log)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error al eliminar entrenamiento: ${e.message}")
            }
        }
    }

    private suspend fun markWorkoutCompletedFromTrainingSession() {
        val today = LocalDateUtils.getTodayString()
        val healthDao = db.healthDao()
        val existing = healthDao.getHealthLogForDateTypeContext(
            dateKey = today,
            type = WellnessEntryType.HABIT.key,
            context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT
        )
        val now = System.currentTimeMillis()
        val autoLog = (existing ?: HealthLogEntity(
            type = WellnessEntryType.HABIT.key,
            value = 1f,
            unit = "check-in",
            context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT,
            note = "Entrenamiento registrado automaticamente",
            dateKey = today,
            createdAt = now,
            timestamp = now,
            isAutoWorkout = true
        )).copy(
            value = 1f,
            unit = "check-in",
            context = WellnessEntryRules.AUTO_WORKOUT_CONTEXT,
            note = "Entrenamiento registrado automaticamente",
            updatedAt = now,
            isAutoWorkout = true
        )
        healthDao.insertHealthLog(autoLog)
    }

    private suspend fun triggerGamificationHook(actionKey: String) {
        try {
            val todayStr = LocalDateUtils.getTodayString()
            val activityDao = db.dailyActivityDao()
            val current = activityDao.getActivityForDay("local_user", todayStr) ?: DailyActivityEntity(
                userId = "local_user",
                date = todayStr,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val updated = when (actionKey) {
                "log_workout" -> current.copy(hasWorkoutLog = true, isActiveDay = true, updatedAt = System.currentTimeMillis())
                else -> current.copy(isActiveDay = true, updatedAt = System.currentTimeMillis())
            }
            activityDao.insertActivity(updated)

            val streakRepo = StreakRepository(db.streakDao())
            val activityRepo = DailyActivityRepository(db.dailyActivityDao())
            val streakManager = StreakManager(streakRepo, activityRepo)
            val (newStreak, wasReset) = streakManager.updateStreak("local_user")

            val missionRepo = DailyMissionRepository(db.dailyMissionDao())
            val achRepo = AchievementRepository(db.achievementDao())
            val achManager = AchievementManager(achRepo)
            val missionManager = DailyMissionManager(missionRepo, streakManager, achManager)
            missionManager.triggerMissionCheck("local_user", actionKey)

            if (actionKey == "log_workout") {
                achManager.checkAndAward("local_user", "ach_first_workout")
            }

            if (newStreak >= 3) {
                achManager.checkAndAward("local_user", "ach_streak_3")
            }
            if (newStreak >= 7) {
                achManager.checkAndAward("local_user", "ach_streak_7")
            }
            if (newStreak >= 14) {
                achManager.checkAndAward("local_user", "ach_streak_14")
            }
            if (newStreak >= 30) {
                achManager.checkAndAward("local_user", "ach_streak_30")
            }
            if (wasReset) {
                achManager.checkAndAward("local_user", "ach_epic_return")
            }

            val userLogsCount = activityRepo.getAllActivitiesFlow("local_user").first()
            val activeWeekDays = userLogsCount.take(7).count { it.isActiveDay }
            if (activeWeekDays == 7) {
                achManager.checkAndAward("local_user", "ach_active_week")
            }
        } catch (e: Exception) {
            // Keep it completely silent
        }
    }
}
