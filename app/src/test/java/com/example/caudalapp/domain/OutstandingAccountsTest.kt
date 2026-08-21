package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OutstandingAccountsTest {
    private val bags = Product("bags-account", "Bolsa")
    private val jug = Product("jug-account", "Garrafón", returnable = true)

    @Test
    fun `deuda envases y entrega pendiente continúan en la siguiente ruta`() {
        val firstRoute = RouteLedger.start(0, mapOf(bags to 10, jug to 2))
        firstRoute.registerStoreSale(
            "store-account",
            listOf(
                SaleLine(bags, requested = 12, delivered = 10, total = 50),
                SaleLine(jug, requested = 2, delivered = 2, total = 16, emptyContainersReceived = 1),
            ),
            amountReceived = 0,
        )

        val nextRoute = RouteLedger.start(100, mapOf(bags to 20, jug to 5))
        nextRoute.importAccounts(firstRoute.outstandingAccountStates())

        val account = nextRoute.storeAccount("store-account")
        assertEquals(2, account.unitsPending(bags.id))
        assertEquals(1, account.containersDue(jug.id))
        assertEquals(66, account.paymentPending(bags.id))
    }

    @Test
    fun `cuentas completamente resueltas no se trasladan a otra ruta`() {
        val firstRoute = RouteLedger.start(0, mapOf(bags to 1))
        firstRoute.registerStoreSale(
            "store-paid",
            listOf(SaleLine(bags, 1, 1, 10)),
            amountReceived = 0,
        )
        firstRoute.collectDebt("store-paid", 10)

        assertEquals(emptyList<StoreAccountState>(), firstRoute.outstandingAccountStates())
    }
}
