package com.lockcal.app

import android.app.Application
import com.lockcal.app.service.CalendarSyncWorker
import com.lockcal.app.service.LockscreenNotificationManager

class LockCalApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize persistent lockscreen notification
        val notificationManager = LockscreenNotificationManager(this)
        notificationManager.updateLockscreenNotification()

        // Schedule periodic background sync worker (every 30 minutes)
        CalendarSyncWorker.schedulePeriodicSync(this)
    }
}
