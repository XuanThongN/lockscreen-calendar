package com.lockcal.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lockcal.app.model.CalendarEvent
import com.lockcal.app.model.CalendarFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CalendarRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lockcal_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
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

    suspend fun syncAllFeeds(): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val feeds = getFeeds().filter { it.enabled }
        if (feeds.isEmpty()) {
            prefs.edit().putString(KEY_CACHED_EVENTS, gson.toJson(emptyList<CalendarEvent>())).apply()
            return@withContext Result.success(emptyList())
        }

        val allEvents = mutableListOf<CalendarEvent>()
        var hasAtLeastOneSuccess = false
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
                    .header("User-Agent", "LockCal-Android/1.0")
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
                    }
                }
            } catch (e: Exception) {
                // Log and continue to next feed so one bad feed does not break all others
            }
        }

        if (hasAtLeastOneSuccess || feeds.isEmpty()) {
            val sortedEvents = allEvents.distinctBy { it.uid + it.startMillis }.sorted()
            prefs.edit()
                .putString(KEY_CACHED_EVENTS, gson.toJson(sortedEvents))
                .putString(KEY_FEEDS, gson.toJson(updatedFeeds))
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            Result.success(sortedEvents)
        } else {
            Result.failure(Exception("Failed to synchronize feeds. Check URLs and connection."))
        }
    }
}
