package com.ataraxia.domain.model

import kotlinx.datetime.LocalDate

data class FocusRecord(val id: String, val date: LocalDate, val durationSeconds: Long, val completedAt: Long) {
    init { require(id.isNotBlank() && durationSeconds in 1..86400) }
}
