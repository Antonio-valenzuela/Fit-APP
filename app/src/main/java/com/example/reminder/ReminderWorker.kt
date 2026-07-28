package com.example.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.LocalDateUtils
import com.example.data.model.isValidOwnerId

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = PreferencesManager(applicationContext)
            val ownerId = prefs.getActiveOwnerId()?.takeIf(::isValidOwnerId)
                ?: return Result.success()
            val settings = prefs.getFeatureSettings()

            // If notification reminders are not enabled globally, skip execution immediately to save battery
            if (!settings.remindersEnabled) {
                return Result.success()
            }

            val db = AppDatabase.getInstance(applicationContext)
            val todayStr = LocalDateUtils.getTodayString()

            // Load activity status of current day
            val dailyActivity = db.dailyActivityDao().getActivityForDay(ownerId, todayStr)
            val hasCheckIn = dailyActivity?.hasCheckIn == true

            // Check missions status of current day
            val missions = db.dailyMissionDao().getMissionsForDay(ownerId, todayStr)
            val pendingMissions = missions.filter { !it.completed }

            // Anti-intrusion policy: If user did check-in and finished missions, do NOT trigger any push
            if (hasCheckIn && pendingMissions.isEmpty()) {
                return Result.success()
            }

            // Custom, non-guilt tone phrases
            val title: String
            val content: String

            if (settings.softMotivationMode) {
                title = "Sin presiones, solo constancia"
                content = when {
                    !hasCheckIn -> "Manten tu ritmo saludable hoy. Tu check-in diario toma solo 30 segundos."
                    pendingMissions.isNotEmpty() -> "Tienes metas simples pendientes hoy. Entra cuando puedas."
                    else -> "Registra agua, comida o sueño a tu propio ritmo."
                }
            } else {
                title = "Tu racha FitPlan te espera"
                content = when {
                    !hasCheckIn -> "Haz tu check-in diario y suma un nuevo dia en tu racha."
                    pendingMissions.isNotEmpty() -> "Te quedan misiones rapidas pendientes hoy."
                    else -> "Sigue sumando hábitos saludables."
                }
            }

            NotificationHelper.showNotification(applicationContext, title, content)
            Result.success()
        } catch (error: Exception) {
            Log.e("ReminderWorker", "Error running reminder worker", error)
            Result.retry()
        }
    }
}

