package com.lockcal.app.data

import com.lockcal.app.model.CalendarEvent
import java.io.BufferedReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object IcsParser {

    /**
     * Parses an RFC 5545 iCalendar string into a sorted list of upcoming CalendarEvents.
     */
    fun parse(icsContent: String, feedName: String, colorHex: String): List<CalendarEvent> {
        val unfoldedLines = unfoldLines(icsContent)
        val events = mutableListOf<CalendarEvent>()

        var inEvent = false
        var uid = ""
        var summary = ""
        var description = ""
        var location = ""
        var dtStartRaw = ""
        var dtEndRaw = ""
        var isAllDay = false
        var rrule = ""

        // Only retain events from beginning of today onwards (up to 45 days)
        val cutoffStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val cutoffEnd = cutoffStart + (45L * 24 * 60 * 60 * 1000)

        for (line in unfoldedLines) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                uid = UUID.randomUUID().toString()
                summary = "Untitled Event"
                description = ""
                location = ""
                dtStartRaw = ""
                dtEndRaw = ""
                isAllDay = false
                rrule = ""
                continue
            }

            if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent && dtStartRaw.isNotEmpty()) {
                    val (startMillis, allDayStart) = parseDate(dtStartRaw)
                    val (endMillis, _) = if (dtEndRaw.isNotEmpty()) {
                        parseDate(dtEndRaw)
                    } else {
                        Pair(startMillis + (if (allDayStart) 86400000L else 3600000L), allDayStart)
                    }

                    val finalAllDay = isAllDay || allDayStart

                    // Basic recurring event handling: if start is before today and has RRULE FREQ=WEEKLY or FREQ=DAILY
                    var effectiveStart = startMillis
                    var effectiveEnd = endMillis
                    val duration = endMillis - startMillis

                    if (rrule.contains("FREQ=WEEKLY", ignoreCase = true) && effectiveEnd < cutoffStart) {
                        val oneWeek = 7L * 24 * 60 * 60 * 1000
                        while (effectiveEnd < cutoffStart) {
                            effectiveStart += oneWeek
                            effectiveEnd += oneWeek
                        }
                    } else if (rrule.contains("FREQ=DAILY", ignoreCase = true) && effectiveEnd < cutoffStart) {
                        val oneDay = 24L * 60 * 60 * 1000
                        while (effectiveEnd < cutoffStart) {
                            effectiveStart += oneDay
                            effectiveEnd += oneDay
                        }
                    }

                    if (effectiveEnd >= cutoffStart && effectiveStart <= cutoffEnd) {
                        events.add(
                            CalendarEvent(
                                uid = uid,
                                summary = summary,
                                description = description,
                                location = location,
                                startMillis = effectiveStart,
                                endMillis = effectiveEnd,
                                isAllDay = finalAllDay,
                                feedName = feedName,
                                colorHex = colorHex
                            )
                        )
                    }
                }
                inEvent = false
                continue
            }

            if (!inEvent) continue

            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue

            val keyPart = line.substring(0, colonIdx)
            val valuePart = line.substring(colonIdx + 1)
            val key = keyPart.split(';')[0].uppercase(Locale.ROOT).trim()

            when (key) {
                "UID" -> uid = valuePart.trim()
                "SUMMARY" -> summary = unescapeText(valuePart)
                "DESCRIPTION" -> description = unescapeText(valuePart)
                "LOCATION" -> location = unescapeText(valuePart)
                "RRULE" -> rrule = valuePart.trim()
                "DTSTART" -> {
                    dtStartRaw = valuePart.trim()
                    if (keyPart.contains("VALUE=DATE", ignoreCase = true) || dtStartRaw.length == 8) {
                        isAllDay = true
                    }
                }
                "DTEND" -> {
                    dtEndRaw = valuePart.trim()
                }
            }
        }

        return events.sorted()
    }

    private fun unfoldLines(icsContent: String): List<String> {
        val result = mutableListOf<String>()
        val reader = BufferedReader(StringReader(icsContent))
        var currentLine: StringBuilder? = null

        var line = reader.readLine()
        while (line != null) {
            if (line.startsWith(" ") || line.startsWith("	")) {
                currentLine?.append(line.substring(1))
            } else {
                if (currentLine != null) {
                    result.add(currentLine.toString())
                }
                currentLine = StringBuilder(line)
            }
            line = reader.readLine()
        }
        if (currentLine != null) {
            result.add(currentLine.toString())
        }
        return result
    }

    private fun parseDate(dateStr: String): Pair<Long, Boolean> {
        val clean = dateStr.trim()
        val utcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val localFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val allDayFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        try {
            if (clean.endsWith("Z")) {
                val d = utcFormat.parse(clean)
                if (d != null) return Pair(d.time, false)
            }
            if (clean.contains("T")) {
                val d = localFormat.parse(clean)
                if (d != null) return Pair(d.time, false)
            }
            val d = allDayFormat.parse(clean)
            if (d != null) return Pair(d.time, true)
        } catch (e: Exception) {
            // Fallback to current time if parsing fails
        }
        return Pair(System.currentTimeMillis(), false)
    }

    private fun unescapeText(text: String): String {
        return text
            .replace("\n", "
")
            .replace("\N", "
")
            .replace("\,", ",")
            .replace("\;", ";")
            .replace("\\", "\")
            .trim()
    }
}
