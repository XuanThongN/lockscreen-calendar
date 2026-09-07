package com.lockcal.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lockcal.app.model.CalendarEvent
import com.lockcal.app.model.CalendarFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class CalendarRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lockcal_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val KEY_FEEDS = "saved_feeds"
        private const val KEY_CACHED_EVENTS = "cached_events"
        private const val KEY_LOCKSCREEN_ENABLED = "lockscreen_enabled"
        private const val KEY_HIDE_PRIVATE = "hide_private"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }

    fun getFeeds(): List<CalendarFeed> {
        val json = prefs.getString(KEY_FEEDS, null) ?: return emptyList()
        val type = object : TypeToken<List<CalendarFeed>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFeed(feed: CalendarFeed) {
        val current = getFeeds().toMutableList()
        val index = current.indexOfFirst { it.id == feed.id }
        if (index != -1) {
            current[index] = feed
        } else {
            current.add(feed)
        }
        prefs.edit().putString(KEY_FEEDS, gson.toJson(current)).apply()
    }

    fun deleteFeed(feedId: String) {
        val current = getFeeds().filter { it.id != feedId }
        prefs.edit().putString(KEY_FEEDS, gson.toJson(current)).apply()
    }

    fun getCachedEvents(): List<CalendarEvent> {
        val json = prefs.getString(KEY_CACHED_EVENTS, null) ?: return emptyList()
        val type = object : TypeToken<List<CalendarEvent>>() {}.type
        return try {
            val list: List<CalendarEvent> = gson.fromJson(json, type) ?: emptyList()
            list.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isLockscreenEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, true)
    }

    fun setLockscreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCKSCREEN_ENABLED, enabled).apply()
    }

    fun isHidePrivateEnabled(): Boolean {
        return prefs.getBoolean(KEY_HIDE_PRIVATE, false)
    }

    fun setHidePrivateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_PRIVATE, enabled).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    fun syncDeviceCalendars(): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LockCal", "READ_CALENDAR permission not granted; skipping device calendar query")
            return emptyList()
        }

        val deviceEvents = mutableListOf<CalendarEvent>()
        val startMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endMillis = startMillis + (60L * 24 * 60 * 60 * 1000)

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR
        )

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx).toString() else UUID.randomUUID().toString()
                    val title = if (titleIdx != -1) cursor.getString(titleIdx).orEmpty().ifBlank { "Untitled Event" } else "Untitled Event"
                    val desc = if (descIdx != -1) cursor.getString(descIdx).orEmpty() else ""
                    val loc = if (locIdx != -1) cursor.getString(locIdx).orEmpty() else ""
                    val begin = if (beginIdx != -1) cursor.getLong(beginIdx) else startMillis
                    val end = if (endIdx != -1) cursor.getLong(endIdx) else (begin + 3600000L)
                    val allDay = if (allDayIdx != -1) cursor.getInt(allDayIdx) == 1 else false
                    val calName = if (calNameIdx != -1) cursor.getString(calNameIdx).orEmpty().ifBlank { "Google Calendar" } else "Google Calendar"
                    val colorInt = if (colorIdx != -1) cursor.getInt(colorIdx) else 0
                    val hexColor = if (colorInt != 0) String.format("#%06X", (0xFFFFFF and colorInt)) else "#3B82F6"

                    deviceEvents.add(
                        CalendarEvent(
                            uid = "device_$id",
                            summary = title,
                            description = desc,
                            location = loc,
                            startMillis = begin,
                            endMillis = if (end <= begin) begin + (if (allDay) 86400000L else 3600000L) else end,
                            isAllDay = allDay,
                            feedName = calName,
                            colorHex = hexColor
                        )
                    )
                }
            }
            Log.d("LockCal", "Retrieved ${deviceEvents.size} events from Android system calendar provider")
        } catch (e: Exception) {
            Log.e("LockCal", "Error reading CalendarContract", e)
        }

        return deviceEvents
    }

    suspend fun syncAllFeeds(): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val allEvents = mutableListOf<CalendarEvent>()
        var hasAtLeastOneSuccess = false

        // 1. Sync on-device Android calendars (Google Calendar accounts, local, Outlook)
        val deviceEvents = syncDeviceCalendars()
        if (deviceEvents.isNotEmpty()) {
            allEvents.addAll(deviceEvents)
            hasAtLeastOneSuccess = true
        }

        // 2. Sync configured iCal/webcal URL feeds
        val feeds = getFeeds().filter { it.enabled }
        val updatedFeeds = getFeeds().toMutableList()

        for (feed in feeds) {
            try {
                // Convert webcal:// to https://
                var fetchUrl = feed.url.trim()
                if (fetchUrl.startsWith("webcal://", ignoreCase = true)) {
                    fetchUrl = "https://" + fetchUrl.substring(9)
                }

                val request = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = IcsParser.parse(body, feed.name, feed.colorHex)
                            allEvents.addAll(parsed)
                            hasAtLeastOneSuccess = true

                            // Update feed stats
                            val idx = updatedFeeds.indexOfFirst { it.id == feed.id }
                            if (idx != -1) {
                                updatedFeeds[idx] = updatedFeeds[idx].copy(
                                    lastSyncTime = System.currentTimeMillis(),
                                    eventCount = parsed.size
                                )
                            }
                        }
                    } else {
                        Log.w("LockCal", "Feed ${feed.name} HTTP error: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("LockCal", "Feed ${feed.name} network/parse error", e)
            }
        }

        val sortedEvents = allEvents.distinctBy { "${it.summary.trim().lowercase()}_${it.startMillis}" }.sorted()
        prefs.edit()
            .putString(KEY_CACHED_EVENTS, gson.toJson(sortedEvents))
            .putString(KEY_FEEDS, gson.toJson(updatedFeeds))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()

        if (hasAtLeastOneSuccess || sortedEvents.isNotEmpty() || feeds.isEmpty()) {
            Result.success(sortedEvents)
        } else {
            Result.failure(Exception("Failed to synchronize feeds. Check calendar permission or feed URLs."))
        }
    }
}
