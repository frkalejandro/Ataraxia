package com.ataraxia.ui.dashboard

import kotlin.test.*

class DashboardSummaryTest {
    @Test fun formatsDailyFocusTotalsWithoutLosingHours() {
        assertEquals("0 min", formatFocusTotal(0))
        assertEquals("25 min", formatFocusTotal(1500))
        assertEquals("1 h 15 min", formatFocusTotal(4500))
    }
}
