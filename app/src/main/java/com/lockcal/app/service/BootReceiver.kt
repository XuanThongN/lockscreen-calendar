package com.lockcal.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Re-schedule background sync worker
            CalendarSyncWorker.schedulePeriodicSync(context)

            // Re-post lock screen notification
            LockscreenNotificationManager(context).updateLockscreenNotification()
        }
    }
}
