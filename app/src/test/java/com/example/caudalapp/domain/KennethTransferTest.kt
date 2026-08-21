package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KennethTransferTest {
    private val bags = Product("bags-transfer", "Bolsa")
    private val jug = Product("jug-transfer", "Garrafón", returnable = true)

    @Test
    fun `dar producto lleno a Kenneth reduce inventario pero no efectivo`() {
        val ledger = RouteLedger.start(200, mapOf(bags to 100))

        ledger.transferWithKenneth(
            direction = TransferDirection.TO_KENNETH,
            lines = listOf(TransferLine(bags, fullUnits = 50)),
        )

        assertEquals(50, ledger.availableStock(bags.id))
        assertEquals(200, ledger.cashOnHand)
        assertEquals(1, ledger.kennethTransfers.size)
    }

    @Test
    fun `recibir producto lleno de Kenneth aumenta inventario`() {
        val ledger = RouteLedger.start(0, mapOf(jug to 4))

        ledger.transferWithKenneth(
            direction = TransferDirection.FROM_KENNETH,
            lines = listOf(TransferLine(jug, fullUnits = 6)),
        )

        assertEquals(10, ledger.availableStock(jug.id))
    }

    @Test
    fun `los envases vacios se transfieren separados de los llenos`() {
        val ledger = RouteLedger.start(
            initialCash = 0,
            initialStock = mapOf(jug to 10),
            initialEmptyContainers = mapOf(jug.id to 8),
        )

        ledger.transferWithKenneth(
            direction = TransferDirection.TO_KENNETH,
            lines = listOf(TransferLine(jug, emptyUnits = 3)),
        )
        ledger.transferWithKenneth(
            direction = TransferDirection.FROM_KENNETH,
            lines = listOf(TransferLine(jug, emptyUnits = 2)),
        )

        assertEquals(7, ledger.emptyContainers(jug.id))
        assertEquals(10, ledger.availableStock(jug.id))
        assertEquals(2, ledger.kennethTransfers.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no permite entregar a Kenneth mas producto del disponible`() {
        val ledger = RouteLedger.start(0, mapOf(bags to 5))

        ledger.transferWithKenneth(
            direction = TransferDirection.TO_KENNETH,
            lines = listOf(TransferLine(bags, fullUnits = 6)),
        )
    }
}
