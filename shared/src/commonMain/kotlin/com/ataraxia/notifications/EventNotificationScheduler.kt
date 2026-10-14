package com.ataraxia.notifications

import com.ataraxia.domain.model.CalendarEvent

interface EventNotificationScheduler {
    fun schedule(event: CalendarEvent)
    fun cancel(id: String)
}

class NoOpEventNotificationScheduler : EventNotificationScheduler {
    override fun schedule(event: CalendarEvent) = Unit
    override fun cancel(id: String) = Unit
}
