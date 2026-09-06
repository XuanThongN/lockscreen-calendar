package com.lockcal.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lockcal.app.R
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.model.CalendarEvent
import com.lockcal.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockscreenNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val repository = CalendarRepository(context)

    companion object {
        const val CHANNEL_ID = "lock_calendar_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SYNC_NOW = "com.lockcal.app.ACTION_SYNC_NOW"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateLockscreenNotification() {
        if (!repository.isLockscreenEnabled()) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val allEvents = repository.getCachedEvents()
        val now = System.currentTimeMillis()
        val upcomingEvents = allEvents.filter { it.endMillis >= now }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val syncIntent = Intent(context, CalendarSyncWorker.SyncReceiver::class.java)
        val pendingSync = PendingIntent.getBroadcast(
            context,
            1,
            syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Collapsed RemoteViews
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_lock_calendar_collapsed)
        val isHidePrivate = repository.isHidePrivateEnabled()

        if (upcomingEvents.isNotEmpty()) {
            val nextEvent = upcomingEvents.first()
            val displayTitle = if (isHidePrivate) "Upcoming Event Scheduled" else nextEvent.summary
            collapsedView.setTextViewText(R.id.tvNotifNextTitle, displayTitle)
            collapsedView.setTextViewText(
                R.id.tvNotifNextTime,
                "${nextEvent.formattedDate()} • ${nextEvent.formattedTime()}"
            )
            collapsedView.setTextViewText(
                R.id.tvNotifEventCount,
                "${upcomingEvents.size} events"
            )
        } else {
            collapsedView.setTextViewText(
                R.id.tvNotifNextTitle,
                context.getString(R.string.notification_no_events)
            )
            collapsedView.setTextViewText(R.id.tvNotifNextTime, "All calendars synced")
            collapsedView.setTextViewText(R.id.tvNotifEventCount, "0 events")
        }

        // Expanded RemoteViews
        val expandedView = RemoteViews(context.packageName, R.layout.notification_lock_calendar_expanded)
        val headerDateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        expandedView.setTextViewText(R.id.tvNotifDateHeader, headerDateFormat.format(Date()))

        val rowLayoutIds = intArrayOf(
            R.id.layoutEvent1,
            R.id.layoutEvent2,
            R.id.layoutEvent3,
            R.id.layoutEvent4
        )
        val timeViewIds = intArrayOf(
            R.id.tvEv1Time,
            R.id.tvEv2Time,
            R.id.tvEv3Time,
            R.id.tvEv4Time
        )
        val titleViewIds = intArrayOf(
            R.id.tvEv1Title,
            R.id.tvEv2Title,
            R.id.tvEv3Title,
            R.id.tvEv4Title
        )

        for (i in 0 until 4) {
            if (i < upcomingEvents.size) {
                val ev = upcomingEvents[i]
                expandedView.setViewVisibility(rowLayoutIds[i], View.VISIBLE)
                val timeLabel = if (ev.isToday()) ev.shortTimeString() else "${ev.formattedDate().take(3)} ${ev.shortTimeString()}"
                expandedView.setTextViewText(timeViewIds[i], timeLabel)

                val displayTitle = if (isHidePrivate) {
                    "Calendar Event (${ev.feedName})"
                } else {
                    "${ev.summary} (${ev.feedName})"
                }
                expandedView.setTextViewText(titleViewIds[i], displayTitle)
            } else {
                expandedView.setViewVisibility(rowLayoutIds[i], View.GONE)
            }
        }

        val remaining = upcomingEvents.size - 4
        if (remaining > 0) {
            expandedView.setTextViewText(
                R.id.tvNotifFooter,
                "+ $remaining more events • Tap to view all"
            )
        } else if (upcomingEvents.isEmpty()) {
            expandedView.setTextViewText(
                R.id.tvNotifFooter,
                "No upcoming events. Tap to configure feeds."
            )
        } else {
            expandedView.setTextViewText(
                R.id.tvNotifFooter,
                "Tap to open full agenda"
            )
        }

        val visibilityMode = if (isHidePrivate) {
            NotificationCompat.VISIBILITY_PRIVATE
        } else {
            NotificationCompat.VISIBILITY_PUBLIC
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calendar)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(visibilityMode)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .addAction(R.drawable.ic_refresh, context.getString(R.string.action_sync_now), pendingSync)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
