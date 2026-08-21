package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountSettlementPolicyTest {
    @Test
    fun `un pago fuera de ruta reduce deudas y cobros pendientes en orden`() {
        val account = StoreAccountState(
            storeId = "store-1",
            debts = listOf(Debt(50, 50, "debt-1")),
            pendingDeliveries = emptyMap(),
            pendingContainers = emptyMap(),
            pendingPayments = mapOf("bags" to 20),
        )

        val updated = AccountSettlementPolicy.registerPayment(account, 60)

        assertEquals(emptyList<Debt>(), updated.debts)
        assertEquals(10, updated.pendingPayments.getValue("bags"))
    }

    @Test
    fun `al resolver todos los pendientes la cuenta deja de estar abierta`() {
        val account = StoreAccountState(
            storeId = "store-1",
            debts = emptyList(),
            pendingDeliveries = mapOf("bags" to 3),
            pendingContainers = mapOf("water-jug" to 2),
            pendingPayments = emptyMap(),
        )

        val updated = AccountSettlementPolicy.receiveContainers(
            AccountSettlementPolicy.completeDelivery(account, "bags"),
            "water-jug",
        )

        assertFalse(AccountSettlementPolicy.hasPending(updated))
    }

    @Test
    fun `una devolucion parcial conserva los envases restantes`() {
        val account = StoreAccountState(
            storeId = "store-1",
            debts = emptyList(),
            pendingDeliveries = mapOf("bags" to 5),
            pendingContainers = mapOf("water-jug" to 3),
            pendingPayments = emptyMap(),
        )

        val withDelivery = AccountSettlementPolicy.completeDelivery(account, "bags", 2)
        val updated = AccountSettlementPolicy.receiveContainers(withDelivery, "water-jug", 1)

        assertEquals(3, updated.pendingDeliveries.getValue("bags"))
        assertEquals(2, updated.pendingContainers.getValue("water-jug"))
    }
}
