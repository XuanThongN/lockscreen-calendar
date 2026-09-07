package com.lockcal.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lockcal.app.R
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.model.CalendarEvent
import com.lockcal.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LockscreenNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val repository = CalendarRepository(context)

    companion object {
        const val CHANNEL_ID = "lock_calendar_channel_v4"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SYNC_NOW = "com.lockcal.app.ACTION_SYNC_NOW"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager.deleteNotificationChannel("lock_calendar_channel")
                notificationManager.deleteNotificationChannel("lock_calendar_channel_v2")
                notificationManager.deleteNotificationChannel("lock_calendar_channel_v3")
            } catch (e: Exception) {
                // Ignore
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateLockscreenNotification() {
        try {
            if (!repository.isLockscreenEnabled()) {
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("LockCal", "POST_NOTIFICATIONS not granted; skipping notification update")
                    return
                }
            }

            val allEvents = repository.getCachedEvents()
            val now = System.currentTimeMillis()
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Events from today onward
            val displayEvents = allEvents.filter { it.endMillis >= startOfToday }
            val nextEvent = displayEvents.firstOrNull { it.endMillis >= now } ?: displayEvents.firstOrNull()

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

            val isHidePrivate = repository.isHidePrivateEnabled()

            val displayTitle = if (nextEvent != null) {
                if (isHidePrivate) "Upcoming Event Scheduled" else nextEvent.summary
            } else {
                context.getString(R.string.notification_no_events)
            }

            val displayTimeSubtitle = if (nextEvent != null) {
                "${nextEvent.formattedDate()} • ${nextEvent.formattedTime()}"
            } else {
                "All calendars synced"
            }

            val eventCountLabel = "${displayEvents.size} events"

            // Collapsed RemoteViews
            val collapsedView = RemoteViews(context.packageName, R.layout.notification_lock_calendar_collapsed)
            collapsedView.setImageViewBitmap(R.id.ivNotifIcon, renderDrawableToBitmap(R.drawable.ic_calendar, 32))
            collapsedView.setTextViewText(R.id.tvNotifNextTitle, displayTitle)
            collapsedView.setTextViewText(R.id.tvNotifNextTime, displayTimeSubtitle)
            collapsedView.setTextViewText(R.id.tvNotifEventCount, eventCountLabel)

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
                if (i < displayEvents.size) {
                    val ev = displayEvents[i]
                    expandedView.setViewVisibility(rowLayoutIds[i], View.VISIBLE)
                    val timeLabel = if (ev.isToday()) ev.shortTimeString() else "${ev.formattedDate().take(3)} ${ev.shortTimeString()}"
                    expandedView.setTextViewText(timeViewIds[i], timeLabel)

                    val itemTitle = if (isHidePrivate) {
                        "Calendar Event (${ev.feedName})"
                    } else {
                        "${ev.summary} (${ev.feedName})"
                    }
                    expandedView.setTextViewText(titleViewIds[i], itemTitle)
                } else {
                    expandedView.setViewVisibility(rowLayoutIds[i], View.GONE)
                }
            }

            val remaining = displayEvents.size - 4
            if (remaining > 0) {
                expandedView.setTextViewText(
                    R.id.tvNotifFooter,
                    "+ $remaining more events • Tap to view all"
                )
            } else if (displayEvents.isEmpty()) {
                expandedView.setTextViewText(
                    R.id.tvNotifFooter,
                    "No events scheduled today. Tap to sync."
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

            // Public version displayed when locked / keyguard cannot render custom views
            val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle(displayTitle)
                .setContentText(displayTimeSubtitle)
                .setSubText(eventCountLabel)
                .setContentIntent(pendingOpenApp)
                .setOngoing(true)
                .build()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle(displayTitle)
                .setContentText(displayTimeSubtitle)
                .setSubText(eventCountLabel)
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(pendingOpenApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(visibilityMode)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPublicVersion(publicNotification)
                .addAction(R.drawable.ic_refresh, context.getString(R.string.action_sync_now), pendingSync)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("LockCal", "Lock screen notification updated successfully")
        } catch (e: Exception) {
            Log.e("LockCal", "Error updating lockscreen notification", e)
        }
    }

    private fun renderDrawableToBitmap(drawableId: Int, sizeDp: Int = 32): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        return bitmap
    }
}
