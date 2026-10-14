package com.ataraxia.domain.model

import kotlinx.datetime.LocalDateTime

/** Events without an end time remain visible for their entire scheduled day. */
fun CalendarEvent.isUpcoming(now: LocalDateTime): Boolean =
    date > now.date || (date == now.date && (isAllDay || endTime == null || endTime > now.time))
