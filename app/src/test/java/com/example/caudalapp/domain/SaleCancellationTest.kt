package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCancellationTest {
    private val bags = Product("bags-audit", "Bolsa")

    @Test
    fun `anular venta restaura inventario efectivo pendientes y conserva evidencia`() {
        val ledger = RouteLedger.start(initialCash = 100, initialStock = mapOf(bags to 15))
        ledger.registerStoreSale(
            "store-a",
            listOf(SaleLine(bags, requested = 18, delivered = 15, total = 50)),
            amountReceived = 0,
        )
        val sale = ledger.sales.single()

        val result = ledger.cancelSale(sale.id, "La tienda cambió la cantidad", 9_000L)

        assertTrue(result.accepted)
        assertEquals(15, ledger.availableStock(bags.id))
        assertEquals(0, ledger.storeAccount("store-a").unitsPending(bags.id))
        assertEquals(0, ledger.totalSales)
        assertTrue(ledger.sales.single().cancelled)
        assertEquals("La tienda cambió la cantidad", ledger.sales.single().cancellationReason)
        assertEquals(RouteAuditType.SALE_CANCELLED, ledger.auditEntries.last().type)
    }

    @Test
    fun `no anula una venta fiada cuya deuda ya recibio un pago`() {
        val ledger = RouteLedger.start(initialCash = 100, initialStock = mapOf(bags to 20))
        ledger.registerStoreSale(
            "store-b",
            listOf(SaleLine(bags, requested = 10, delivered = 10, total = 50)),
            amountReceived = 0,
        )
        val sale = ledger.sales.single()
        ledger.collectDebt("store-b", 10)

        val result = ledger.cancelSale(sale.id, "Error")

        assertFalse(result.accepted)
        assertEquals(10, ledger.availableStock(bags.id))
        assertEquals(50, ledger.totalSales)
    }

    @Test
    fun `registra ventas gastos recargas y transferencias en auditoria`() {
        val ledger = RouteLedger.start(initialCash = 100, initialStock = mapOf(bags to 20))
        ledger.tryRegisterQuickSale(listOf(SaleLine(bags, 1, 1, 5)), 5)
        ledger.registerExpense(ExpenseCategory.REFRESHMENT, 5)
        ledger.restock(mapOf(bags to 10))
        ledger.transferWithKenneth(TransferDirection.TO_KENNETH, listOf(TransferLine(bags, fullUnits = 2)))

        assertEquals(
            listOf(
                RouteAuditType.QUICK_SALE,
                RouteAuditType.EXPENSE,
                RouteAuditType.RESTOCK,
                RouteAuditType.KENNETH_TRANSFER,
            ),
            ledger.auditEntries.map(RouteAuditEntry::type),
        )
    }
}
