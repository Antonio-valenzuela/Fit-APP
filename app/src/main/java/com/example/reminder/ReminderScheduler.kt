package com.example.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_NAME = "fitplan_daily_reminder_work"

    fun scheduleDailyReminder(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Set to run twice a day (every 12 hours) to remind are context-aware (afternoon/night)
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            12, TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }

    fun cancelAllReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
