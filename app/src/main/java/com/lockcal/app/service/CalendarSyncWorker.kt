package com.lockcal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.widget.LockCalendarWidgetProvider
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = CalendarRepository(applicationContext)
        val syncResult = repository.syncAllFeeds()

        // Update persistent lock screen notification
        LockscreenNotificationManager(applicationContext).updateLockscreenNotification()

        // Update home screen / lock screen AppWidget
        LockCalendarWidgetProvider.updateAllWidgets(applicationContext)

        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_TAG = "lockcal_periodic_sync"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
    }

    class SyncReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            triggerImmediateSync(context)
        }
    }
}
