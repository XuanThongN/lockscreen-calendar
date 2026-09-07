package com.lockcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.lockcal.app.R
import com.lockcal.app.data.CalendarRepository
import com.lockcal.app.service.CalendarSyncWorker
import com.lockcal.app.ui.MainActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            CalendarSyncWorker.triggerImmediateSync(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.lockcal.app.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, LockCalendarWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                Log.e("LockCal", "Error updating all widgets", e)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val repository = CalendarRepository(context)
                val now = System.currentTimeMillis()
                val upcoming = repository.getCachedEvents().filter { it.endMillis >= now }

                val views = RemoteViews(context.packageName, R.layout.widget_lock_calendar)

                // Header date
                val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                views.setTextViewText(R.id.tvWidgetDate, dateFormat.format(Date()))

                // Safe Bitmaps for RemoteViews
                views.setImageViewBitmap(R.id.ivWidgetIcon, renderDrawableToBitmap(context, R.drawable.ic_calendar, 20))
                views.setImageViewBitmap(R.id.btnWidgetRefresh, renderDrawableToBitmap(context, R.drawable.ic_refresh, 24))

                // PendingIntent to launch MainActivity on tapping widget body
                val openAppIntent = Intent(context, MainActivity::class.java)
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetRoot, pendingOpen)

                // PendingIntent for refresh button
                val refreshIntent = Intent(context, LockCalendarWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val pendingRefresh = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btnWidgetRefresh, pendingRefresh)

                val rows = intArrayOf(R.id.widgetRow1, R.id.widgetRow2, R.id.widgetRow3)
                val times = intArrayOf(R.id.widgetEv1Time, R.id.widgetEv2Time, R.id.widgetEv3Time)
                val titles = intArrayOf(R.id.widgetEv1Title, R.id.widgetEv2Title, R.id.widgetEv3Title)

                if (upcoming.isEmpty()) {
                    views.setViewVisibility(R.id.tvWidgetEmpty, View.VISIBLE)
                    for (row in rows) {
                        views.setViewVisibility(row, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.tvWidgetEmpty, View.GONE)
                    for (i in 0 until 3) {
                        if (i < upcoming.size) {
                            val event = upcoming[i]
                            views.setViewVisibility(rows[i], View.VISIBLE)
                            val timeStr = if (event.isToday()) event.shortTimeString() else "${event.formattedDate().take(3)} ${event.shortTimeString()}"
                            views.setTextViewText(times[i], timeStr)
                            views.setTextViewText(titles[i], event.summary)
                        } else {
                            views.setViewVisibility(rows[i], View.GONE)
                        }
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("LockCal", "Error updating widget id $appWidgetId", e)
            }
        }

        private fun renderDrawableToBitmap(context: Context, drawableId: Int, sizeDp: Int): Bitmap {
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
}
