package com.ataraxia

import com.ataraxia.domain.model.*
import kotlin.test.*

class FinanceTest {
    private fun entry(category: FinanceCategory, amount: Long) = FinanceEntry(category.name, "Prueba", category, amount, 0)

    @Test fun balanceAndSharesOnlyCountExpensesInTheDonut() {
        val summary = FinanceSummary(listOf(entry(FinanceCategory.INITIAL, 500_000),
            entry(FinanceCategory.SALARY, 200_000), entry(FinanceCategory.FOOD, 30_000),
            entry(FinanceCategory.FOOD, 20_000), entry(FinanceCategory.GAMES, 150_000)))
        assertEquals(500_000L, summary.available)
        assertEquals(200_000L, summary.income)
        assertEquals(200_000L, summary.expenses)
        assertEquals(2, summary.shares.size)
        assertEquals(0.25f, summary.shares.first { it.category == FinanceCategory.FOOD }.fraction)
        assertEquals(1f, summary.shares.sumOf { it.fraction.toDouble() }.toFloat())
    }

    @Test fun emptyAndNegativeBalancesAreSupported() {
        assertTrue(FinanceSummary().shares.isEmpty())
        assertFalse(FinanceSummary().hasInitial)
        val summary = FinanceSummary(listOf(entry(FinanceCategory.INITIAL, 0), entry(FinanceCategory.TOOLS, 500)))
        assertTrue(summary.hasInitial)
        assertEquals(-500L, summary.available)
        assertEquals("−$500", formatPesos(summary.available))
    }

    @Test fun validatesWholePesosWithoutSilentlyChangingMalformedAmounts() {
        assertEquals(1_234_567L, parseFinanceAmount("1.234.567"))
        assertEquals(1_234_567L, parseFinanceAmount(" 1234567 "))
        assertEquals(0L, parseFinanceAmount("0"))
        listOf("", "-5", "1,50", "1.50", "1.2345", "1e6", "NaN", "9999999999999999999999", "1000000000001").forEach {
            assertNull(parseFinanceAmount(it), it)
        }
        assertEquals("$1.234.567", formatPesos(1_234_567))
        assertFailsWith<IllegalArgumentException> { entry(FinanceCategory.FOOD, 0) }
        assertFailsWith<IllegalArgumentException> { entry(FinanceCategory.GIFT, -1) }
        assertFailsWith<IllegalArgumentException> { FinanceEntry("id", " ", FinanceCategory.GIFT, 1, 0) }
    }
}
