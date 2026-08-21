package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteClosingTest {
    private val bags = Product(id = "bags-close", name = "Bolsa")
    private val jug = Product(id = "jug-close", name = "Garrafón", returnable = true)

    @Test
    fun `el cierre separa ventas efectivo fiado gastos y sencillo inicial`() {
        val ledger = RouteLedger.start(
            initialCash = 200,
            initialStock = mapOf(bags to 30),
        )
        ledger.registerStoreSale(
            storeId = "store-1",
            lines = listOf(SaleLine(bags, requested = 18, delivered = 18, total = 50)),
            amountReceived = 30,
        )
        ledger.registerExpense(ExpenseCategory.FUEL, amount = 10)

        val summary = ledger.closeSummary()

        assertEquals(200, summary.initialCash)
        assertEquals(50, summary.totalSales)
        assertEquals(30, summary.cashCollected)
        assertEquals(20, summary.moneyPending)
        assertEquals(10, summary.totalExpenses)
        assertEquals(220, summary.expectedCash)
    }

    @Test
    fun `el cierre advierte entregas envases e inventario negativo sin impedir finalizar`() {
        val ledger = RouteLedger.start(
            initialCash = 0,
            initialStock = mapOf(bags to 15, jug to 2),
        )
        ledger.registerStoreSale(
            storeId = "store-pending",
            lines = listOf(SaleLine(bags, requested = 18, delivered = 15, total = 50)),
            amountReceived = 0,
        )
        ledger.registerStoreSale(
            storeId = "store-jugs",
            lines = listOf(SaleLine(jug, requested = 2, delivered = 2, total = 16)),
            amountReceived = 16,
        )

        val summary = ledger.closeSummary()

        assertEquals(3, summary.pendingDeliveryUnits)
        assertEquals(2, summary.pendingContainers)
        assertEquals(-3, summary.negativeStock.getValue(bags.name))
        assertTrue(summary.hasWarnings)
    }

    @Test
    fun `cobrar una deuda anterior suma efectivo pero no duplica las ventas de hoy`() {
        val ledger = RouteLedger.start(initialCash = 100, initialStock = mapOf(bags to 10))
        ledger.registerStoreSale(
            storeId = "store-debt",
            lines = listOf(SaleLine(bags, requested = 1, delivered = 1, total = 20)),
            amountReceived = 0,
        )
        ledger.collectDebt("store-debt", 20)

        val summary = ledger.closeSummary()

        assertEquals(20, summary.totalSales)
        assertEquals(20, summary.cashCollected)
        assertEquals(0, summary.moneyPending)
        assertEquals(120, summary.expectedCash)
    }
}
