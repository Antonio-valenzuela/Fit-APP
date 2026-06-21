package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.AchievementEntity
import com.example.data.model.DailyActivityEntity
import com.example.data.model.DailyMissionEntity
import com.example.data.model.StreakEntity
import com.example.data.repository.*
import com.example.ui.components.MotivationMessageProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MotivationUiState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val missionsCompleted: Int = 0,
    val totalMissions: Int = 0,
    val motivationMessage: String = "",
    val unlockedAchievement: AchievementEntity? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class StreakViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val streakRepo = StreakRepository(db.streakDao())
    private val activityRepo = DailyActivityRepository(db.dailyActivityDao())
    private val streakManager = StreakManager(streakRepo, activityRepo)
    private val prefs = PreferencesManager(application)

    private val _streakState = MutableStateFlow<StreakEntity?>(null)
    val streakState: StateFlow<StreakEntity?> = _streakState.asStateFlow()

    private val _uiState = MutableStateFlow(MotivationUiState())
    val uiState: StateFlow<MotivationUiState> = _uiState.asStateFlow()

    init {
        refreshStreak()
    }

    fun refreshStreak() {
        val features = prefs.getFeatureSettings()
        if (!features.streakEnabled) {
            _uiState.value = MotivationUiState()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val streak = streakManager.checkAndRefreshStreakStatus("local_user")
                _streakState.value = streak

                val todayStr = LocalDateUtils.getTodayString()
                val todayActivity = activityRepo.getActivityForDay("local_user", todayStr)
                val hasCheckIn = todayActivity?.hasCheckIn == true

                val todayMissions = db.dailyMissionDao().getMissionsForDay("local_user", todayStr)
                val comp = todayMissions.count { it.completed }
                val tot = todayMissions.size

                val msg = MotivationMessageProvider.getMessage(
                    currentStreak = streak.currentStreak,
                    missionsCompleted = comp,
                    totalMissions = tot,
                    hasCheckedInToday = hasCheckIn,
                    softMotivationMode = features.softMotivationMode
                )

                _uiState.update {
                    it.copy(
                        currentStreak = streak.currentStreak,
                        bestStreak = streak.bestStreak,
                        missionsCompleted = comp,
                        totalMissions = tot,
                        motivationMessage = msg,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun performDailyCheckIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val todayStr = LocalDateUtils.getTodayString()
                val currentActivity = activityRepo.getActivityForDay("local_user", todayStr) ?: DailyActivityEntity(
                    userId = "local_user",
                    date = todayStr,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val updatedActivity = currentActivity.copy(
                    hasCheckIn = true,
                    isActiveDay = true,
                    updatedAt = System.currentTimeMillis()
                )
                activityRepo.saveActivity(updatedActivity)

                val (streakVal, wasReset) = streakManager.updateStreak("local_user")

                val missionRepo = DailyMissionRepository(db.dailyMissionDao())
                val achRepo = AchievementRepository(db.achievementDao())
                val achManager = AchievementManager(achRepo)
                val missionManager = DailyMissionManager(missionRepo, streakManager, achManager)
                missionManager.triggerMissionCheck("local_user", "log_checkin")

                // First check-in achievement check
                val unlockedFirstLog = achManager.checkAndAward("local_user", "ach_first_checkin")
                if (unlockedFirstLog != null) {
                    _uiState.update { it.copy(unlockedAchievement = unlockedFirstLog) }
                }

                if (streakVal >= 3) {
                    val unlocked = achManager.checkAndAward("local_user", "ach_streak_3")
                    if (unlocked != null) {
                        _uiState.update { it.copy(unlockedAchievement = unlocked) }
                    }
                }
                if (streakVal >= 7) {
                    val unlocked = achManager.checkAndAward("local_user", "ach_streak_7")
                    if (unlocked != null) {
                        _uiState.update { it.copy(unlockedAchievement = unlocked) }
                    }
                }
                if (streakVal >= 14) {
                    val unlocked = achManager.checkAndAward("local_user", "ach_streak_14")
                    if (unlocked != null) {
                        _uiState.update { it.copy(unlockedAchievement = unlocked) }
                    }
                }
                if (streakVal >= 30) {
                    val unlocked = achManager.checkAndAward("local_user", "ach_streak_30")
                    if (unlocked != null) {
                        _uiState.update { it.copy(unlockedAchievement = unlocked) }
                    }
                }
                if (wasReset) {
                    val unlocked = achManager.checkAndAward("local_user", "ach_epic_return")
                    if (unlocked != null) {
                        _uiState.update { it.copy(unlockedAchievement = unlocked) }
                    }
                }

                refreshStreak()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun dismissUnlockedAchievement() {
        _uiState.update { it.copy(unlockedAchievement = null) }
    }
}

class DailyMissionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val missionRepo = DailyMissionRepository(db.dailyMissionDao())
    private val streakRepo = StreakRepository(db.streakDao())
    private val activityRepo = DailyActivityRepository(db.dailyActivityDao())
    private val streakManager = StreakManager(streakRepo, activityRepo)
    private val achRepo = AchievementRepository(db.achievementDao())
    private val achManager = AchievementManager(achRepo)
    private val missionManager = DailyMissionManager(missionRepo, streakManager, achManager)
    private val prefs = PreferencesManager(application)

    private val _missions = MutableStateFlow<List<DailyMissionEntity>>(emptyList())
    val missions: StateFlow<List<DailyMissionEntity>> = _missions.asStateFlow()

    init {
        loadMissions()
    }

    fun loadMissions() {
        if (!prefs.getFeatureSettings().dailyMissionsEnabled) {
            _missions.value = emptyList()
            return
        }

        viewModelScope.launch {
            val list = missionManager.getMissionsForToday("local_user")
            _missions.value = list
        }
    }

    fun forceToggleMission(mission: DailyMissionEntity) {
        viewModelScope.launch {
            val nextStatus = !mission.completed
            val timestamp = if (nextStatus) System.currentTimeMillis() else null
            missionRepo.updateMissionStatus(mission.id, nextStatus, timestamp)

            if (nextStatus) {
                streakManager.updateStreak("local_user")
            }
            loadMissions()
        }
    }

    fun completeMissionDirectly(key: String) {
        viewModelScope.launch {
            val didComplete = missionManager.triggerMissionCheck("local_user", key)
            if (didComplete) {
                loadMissions()
            }
        }
    }
}

class AchievementViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val achRepo = AchievementRepository(db.achievementDao())
    private val prefs = PreferencesManager(application)

    private val _achievements = MutableStateFlow<List<AchievementEntity>>(emptyList())
    val achievements: StateFlow<List<AchievementEntity>> = _achievements.asStateFlow()

    init {
        loadAchievements()
    }

    fun loadAchievements() {
        if (!prefs.getFeatureSettings().achievementsEnabled) {
            _achievements.value = emptyList()
            return
        }

        viewModelScope.launch {
            val list = achRepo.getAchievements("local_user")
            _achievements.value = list
        }
    }
}
