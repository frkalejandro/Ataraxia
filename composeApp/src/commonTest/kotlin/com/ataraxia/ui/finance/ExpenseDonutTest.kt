package com.ataraxia.ui.finance

import com.ataraxia.domain.model.*
import kotlin.test.*

class ExpenseDonutTest {
    private val shares = listOf(ExpenseShare(FinanceCategory.FOOD, 1, 0.25f), ExpenseShare(FinanceCategory.GAMES, 3, 0.75f))

    @Test fun tapsSelectTheCorrectClockwiseArcAndIgnoreHoleAndOutside() {
        assertEquals(FinanceCategory.FOOD, expenseAtPoint(150f, 50f, 200f, shares))
        assertEquals(FinanceCategory.GAMES, expenseAtPoint(175f, 110f, 200f, shares))
        assertEquals(FinanceCategory.GAMES, expenseAtPoint(25f, 100f, 200f, shares))
        assertNull(expenseAtPoint(100f, 100f, 200f, shares))
        assertNull(expenseAtPoint(0f, 0f, 200f, shares))
        assertNull(expenseAtPoint(175f, 100f, 200f, emptyList()))
    }

    @Test fun percentagesRemainMeaningfulForSmallCategories() {
        assertEquals("25 %", formatPercent(0.25f))
        assertEquals("33,3 %", formatPercent(1f / 3))
        assertEquals("<0,1 %", formatPercent(0.00001f))
    }
}
