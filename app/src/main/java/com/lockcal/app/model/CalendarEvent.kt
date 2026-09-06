package com.lockcal.app.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarEvent(
    val uid: String,
    val summary: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean = false,
    val feedName: String = "",
    val colorHex: String = "#3B82F6"
) : Comparable<CalendarEvent> {

    override fun compareTo(other: CalendarEvent): Int {
        return this.startMillis.compareTo(other.startMillis)
    }

    fun isToday(): Boolean {
        val eventCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val nowCal = Calendar.getInstance()
        return eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                eventCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    }

    fun isTomorrow(): Boolean {
        val eventCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return eventCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                eventCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)
    }

    fun formattedDate(): String {
        return when {
            isToday() -> "Today"
            isTomorrow() -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(startMillis))
        }
    }

    fun formattedTime(): String {
        if (isAllDay) return "All Day"
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startStr = timeFormat.format(Date(startMillis))
        return if (endMillis > startMillis) {
            val endStr = timeFormat.format(Date(endMillis))
            "$startStr - $endStr"
        } else {
            startStr
        }
    }

    fun shortTimeString(): String {
        if (isAllDay) return "All Day"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
    }
}
