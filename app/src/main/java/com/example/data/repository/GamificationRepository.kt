package com.example.data.repository

import com.example.data.local.DailyActivityDao
import com.example.data.local.StreakDao
import com.example.data.local.DailyMissionDao
import com.example.data.local.AchievementDao
import com.example.data.model.DailyActivityEntity
import com.example.data.model.StreakEntity
import com.example.data.model.DailyMissionEntity
import com.example.data.model.AchievementEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

object LocalDateUtils {
    fun getTodayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getYesterdayString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    fun getWeekDatesOfCurrentWeek(): List<String> {
        val dates = mutableListOf<String>()
        val cal = Calendar.getInstance()
        // Set to first day of current week (Monday)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0..6) {
            dates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }
}

class DailyActivityRepository(private val dao: DailyActivityDao) {
    suspend fun getActivityForDay(userId: String, date: String): DailyActivityEntity? {
        return dao.getActivityForDay(userId, date)
    }

    fun getActivityForDayFlow(userId: String, date: String): Flow<DailyActivityEntity?> {
        return dao.getActivityForDayFlow(userId, date)
    }

    fun getAllActivitiesFlow(userId: String): Flow<List<DailyActivityEntity>> {
        return dao.getAllActivitiesFlow(userId)
    }

    suspend fun saveActivity(activity: DailyActivityEntity) {
        dao.insertActivity(activity)
    }
}

class StreakRepository(private val dao: StreakDao) {
    suspend fun getStreak(userId: String): StreakEntity? {
        return dao.getStreak(userId)
    }

    fun getStreakFlow(userId: String): Flow<StreakEntity?> {
        return dao.getStreakFlow(userId)
    }

    suspend fun saveStreak(streak: StreakEntity) {
        dao.insertStreak(streak)
    }
}

class DailyMissionRepository(private val dao: DailyMissionDao) {
    suspend fun getMissionsForDay(userId: String, date: String): List<DailyMissionEntity> {
        return dao.getMissionsForDay(userId, date)
    }

    fun getMissionsForDayFlow(userId: String, date: String): Flow<List<DailyMissionEntity>> {
        return dao.getMissionsForDayFlow(userId, date)
    }

    suspend fun saveMissions(missions: List<DailyMissionEntity>) {
        dao.insertMissions(missions)
    }

    suspend fun updateMissionStatus(userId: String, id: Long, completed: Boolean, completedAt: Long?) {
        dao.updateMissionStatus(userId, id, completed, completedAt)
    }

    suspend fun updateMissionStatusByKey(userId: String, date: String, missionKey: String, completed: Boolean, completedAt: Long?) {
        dao.updateMissionStatusByKey(userId, date, missionKey, completed, completedAt)
    }
}

class AchievementRepository(private val dao: AchievementDao) {
    fun getAchievementsFlow(userId: String): Flow<List<AchievementEntity>> {
        return dao.getAchievementsFlow(userId)
    }

    suspend fun getAchievements(userId: String): List<AchievementEntity> {
        return dao.getAchievements(userId)
    }

    suspend fun unlockAchievement(achievement: AchievementEntity) {
        dao.insertAchievement(achievement)
    }
}

// ==========================================
// GAMIFICATION MANAGERS & LOGIC HANDLERS
// ==========================================

class StreakManager(
    private val streakRepo: StreakRepository,
    private val activityRepo: DailyActivityRepository
) {
    suspend fun updateStreak(userId: String): Pair<Int, Boolean> {
        val todayStr = LocalDateUtils.getTodayString()
        val yesterdayStr = LocalDateUtils.getYesterdayString()

        // Get activity of today and ensure it is marked active
        val currentActivity = activityRepo.getActivityForDay(userId, todayStr) ?: DailyActivityEntity(
            userId = userId,
            date = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val updatedActivity = currentActivity.copy(
            isActiveDay = true,
            updatedAt = System.currentTimeMillis()
        )
        activityRepo.saveActivity(updatedActivity)

        // Process Streak calculation
        val streak = streakRepo.getStreak(userId) ?: StreakEntity(
            userId = userId,
            currentStreak = 0,
            bestStreak = 0,
            lastActiveDate = null,
            updatedAt = System.currentTimeMillis()
        )

        var newCurrent = streak.currentStreak
        var wasReset = false

        if (streak.lastActiveDate == todayStr) {
            // Already processed active actions today, do lock count update
            return Pair(streak.currentStreak, false)
        }

        if (streak.lastActiveDate == yesterdayStr) {
            // Continued streak
            newCurrent += 1
        } else {
            // Started today from scratch or returned after a broken streak
            if (streak.lastActiveDate != null) {
                wasReset = true
            }
            newCurrent = 1
        }

        val newBest = maxOf(newCurrent, streak.bestStreak)
        val updatedStreak = streak.copy(
            currentStreak = newCurrent,
            bestStreak = newBest,
            lastActiveDate = todayStr,
            updatedAt = System.currentTimeMillis()
        )
        streakRepo.saveStreak(updatedStreak)
        return Pair(newCurrent, wasReset)
    }

    suspend fun checkAndRefreshStreakStatus(userId: String): StreakEntity {
        val streak = streakRepo.getStreak(userId) ?: StreakEntity(
            userId = userId,
            currentStreak = 0,
            bestStreak = 0,
            lastActiveDate = null,
            updatedAt = System.currentTimeMillis()
        )

        val todayStr = LocalDateUtils.getTodayString()
        val yesterdayStr = LocalDateUtils.getYesterdayString()

        if (streak.lastActiveDate != null && 
            streak.lastActiveDate != todayStr && 
            streak.lastActiveDate != yesterdayStr) {
            // Streak broken since no actions were performed yesterday
            val resetStreak = streak.copy(
                currentStreak = 0,
                updatedAt = System.currentTimeMillis()
            )
            streakRepo.saveStreak(resetStreak)
            return resetStreak
        }
        return streak
    }
}

class DailyMissionManager(
    private val missionRepo: DailyMissionRepository,
    private val streakManager: StreakManager,
    private val achievementManager: AchievementManager
) {
    private val missionsPool = listOf(
        Pair("log_water", Pair("Registrar agua", "Registra al menos una toma de agua")),
        Pair("log_food", Pair("Registrar una comida", "Agrega una comida, cena o colación")),
        Pair("log_workout", Pair("Completar entrenamiento", "Suma una sesión de actividad física hoy")),
        Pair("log_weight", Pair("Registrar tu peso", "Agrega tu peso corporal en Salud")),
        Pair("log_checkin", Pair("Completar check-in", "Haz un check-in rápido de bienestar")),
        Pair("log_sleep", Pair("Registrar descanso", "Registra tus horas y calidad de sueño"))
    )

    suspend fun getMissionsForToday(userId: String): List<DailyMissionEntity> {
        val todayStr = LocalDateUtils.getTodayString()
        val existing = missionRepo.getMissionsForDay(userId, todayStr)
        if (existing.isNotEmpty()) {
            return existing
        }

        // Generate 3 unique tasks deterministically/randomly
        val selected = missionsPool.shuffled(Random(System.currentTimeMillis() / 86400000)).take(3)
        val newMissions = selected.map { (key, pair) ->
            DailyMissionEntity(
                userId = userId,
                date = todayStr,
                missionKey = key,
                title = pair.first,
                description = pair.second,
                completed = false,
                completedAt = null
            )
        }
        missionRepo.saveMissions(newMissions)
        return newMissions
    }

    suspend fun triggerMissionCheck(userId: String, missionKey: String): Boolean {
        val todayStr = LocalDateUtils.getTodayString()
        val missions = missionRepo.getMissionsForDay(userId, todayStr)
        val matched = missions.firstOrNull { it.missionKey == missionKey && !it.completed }
        if (matched != null) {
            missionRepo.updateMissionStatus(userId, matched.id, true, System.currentTimeMillis())
            
            // Mark action in active activities log
            streakManager.updateStreak(userId)

            // Re-fetch to check if all 3 missions of today are completed
            val updatedMissions = missionRepo.getMissionsForDay(userId, todayStr)
            if (updatedMissions.isNotEmpty() && updatedMissions.all { it.completed }) {
                achievementManager.checkAndAward(userId, "ach_all_missions")
            }
            return true
        }
        return false
    }
}

class AchievementManager(private val achievementRepo: AchievementRepository) {

    suspend fun checkAndAward(userId: String, key: String): AchievementEntity? {
        val current = achievementRepo.getAchievements(userId)
        if (current.any { it.achievementKey == key }) {
            return null // Already unlocked
        }

        val pair = when (key) {
            "ach_first_checkin" -> Pair("Primer check-in", "Completaste tu primer registro diario")
            "ach_first_food" -> Pair("Primera comida", "Comenzaste a registrar tu alimentación")
            "ach_first_workout" -> Pair("Primera rutina", "Registraste tu primer entrenamiento")
            "ach_streak_3" -> Pair("Tres días constantes", "Mantuviste tu racha por 3 días")
            "ach_streak_7" -> Pair("Semana completa", "Lograste 7 días de constancia")
            "ach_streak_14" -> Pair("Dos semanas", "Completaste 14 días de seguimiento")
            "ach_streak_30" -> Pair("Mes constante", "Alcanzaste una racha de 30 días")
            "ach_active_week" -> Pair("Semana activa", "Registraste actividad todos los días de la semana")
            "ach_all_missions" -> Pair("Misiones completas", "Completaste tus misiones del día")
            "ach_hydration" -> Pair("Hidratación constante", "Registraste agua durante tres días")
            "ach_epic_return" -> Pair("De vuelta", "Retomaste tu racha después de una pausa")
            else -> Pair("Insignia saludable", "Otorgada por mantener tus hábitos")
        }

        val entity = AchievementEntity(
            userId = userId,
            achievementKey = key,
            title = pair.first,
            description = pair.second,
            unlockedAt = System.currentTimeMillis()
        )
        achievementRepo.unlockAchievement(entity)
        return entity
    }
}

