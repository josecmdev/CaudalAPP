package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCancellationTest {
    @Test
    fun `anular gasto devuelve efectivo y conserva el movimiento`() {
        val ledger = RouteLedger.start(initialCash = 200, initialStock = emptyMap())
        ledger.registerExpense(ExpenseCategory.FUEL, 50)
        val expense = ledger.expenses.single()

        assertTrue(ledger.cancelExpense(expense.id, "Monto escrito incorrectamente", 5_000L))

        assertEquals(200, ledger.cashOnHand)
        assertEquals(0, ledger.totalExpenses)
        assertTrue(ledger.expenses.single().cancelled)
        assertEquals(RouteAuditType.EXPENSE_CANCELLED, ledger.auditEntries.last().type)
        assertFalse(ledger.cancelExpense(expense.id, "Segundo intento"))
    }
}
