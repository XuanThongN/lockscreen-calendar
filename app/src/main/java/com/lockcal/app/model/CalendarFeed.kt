package com.lockcal.app.model

import java.util.UUID

data class CalendarFeed(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var url: String,
    var colorHex: String = "#3B82F6",
    var enabled: Boolean = true,
    var lastSyncTime: Long = 0L,
    var eventCount: Int = 0
)
