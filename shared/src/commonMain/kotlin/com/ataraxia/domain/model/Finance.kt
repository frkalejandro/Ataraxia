package com.ataraxia.domain.model

enum class FinanceKind(val label: String) {
    INITIAL("Saldo inicial"), INCOME("Ingreso"), EXPENSE("Gasto")
}

enum class FinanceCategory(val label: String, val kind: FinanceKind) {
    INITIAL("Saldo inicial", FinanceKind.INITIAL),
    SALARY("Sueldo", FinanceKind.INCOME),
    GIFT("Regalo", FinanceKind.INCOME),
    TRANSFER("Transferencia", FinanceKind.INCOME),
    CLOTHING("Ropa", FinanceKind.EXPENSE),
    FOOD("Comida", FinanceKind.EXPENSE),
    FRIENDS("Salir con amigos", FinanceKind.EXPENSE),
    GAMES("Juegos", FinanceKind.EXPENSE),
    APPLIANCES("Electrodomésticos", FinanceKind.EXPENSE),
    TOOLS("Herramientas", FinanceKind.EXPENSE),
}

const val MAX_FINANCE_AMOUNT = 1_000_000_000_000L

data class FinanceEntry(
    val id: String,
    val name: String,
    val category: FinanceCategory,
    val amount: Long,
    val createdAt: Long,
) {
    val kind: FinanceKind get() = category.kind
    init {
        require(name.isNotBlank() && name.length <= 100)
        require(amount in (if (kind == FinanceKind.INITIAL) 0L else 1L)..MAX_FINANCE_AMOUNT)
    }
}

data class ExpenseShare(val category: FinanceCategory, val amount: Long, val fraction: Float)

data class FinanceSummary(val entries: List<FinanceEntry> = emptyList()) {
    val hasInitial = entries.any { it.kind == FinanceKind.INITIAL }
    val initial = entries.filter { it.kind == FinanceKind.INITIAL }.sumOf { it.amount }
    val income = entries.filter { it.kind == FinanceKind.INCOME }.sumOf { it.amount }
    val expenses = entries.filter { it.kind == FinanceKind.EXPENSE }.sumOf { it.amount }
    val available = initial + income - expenses
    val shares = FinanceCategory.entries.filter { it.kind == FinanceKind.EXPENSE }.mapNotNull { category ->
        val amount = entries.filter { it.category == category }.sumOf { it.amount }
        if (amount == 0L) null else ExpenseShare(category, amount, (amount.toDouble() / expenses).toFloat())
    }
}

/** CLP accepts whole pesos, optionally grouped with dots (e.g. 1.250.000). */
fun parseFinanceAmount(text: String): Long? {
    val value = text.trim()
    if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)").matches(value)) return null
    return value.replace(".", "").toLongOrNull()?.takeIf { it in 0..MAX_FINANCE_AMOUNT }
}

fun formatPesos(amount: Long): String {
    val digits = amount.toString().removePrefix("-").reversed().chunked(3).joinToString(".").reversed()
    return "${if (amount < 0) "−" else ""}$$digits"
}
