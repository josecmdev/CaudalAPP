package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLedgerTest {

    private val bolsas = Product(id = "bolsas", name = "Bolsa", defaultSuggestedTotal = 50)
    private val garrafon = Product(
        id = "garrafon",
        name = "Garrafón",
        defaultSuggestedTotal = 8,
        returnable = true,
    )

    @Test
    fun `una venta parcialmente pagada aumenta efectivo y crea deuda por el saldo`() {
        val ledger = RouteLedger.start(
            initialCash = 200,
            initialStock = mapOf(bolsas to 100),
        )

        val result = ledger.registerStoreSale(
            storeId = "tienda-lupita",
            lines = listOf(SaleLine(product = bolsas, requested = 18, delivered = 18, total = 50)),
            amountReceived = 30,
        )

        assertEquals(230, result.cashOnHand)
        assertEquals(82, result.availableStock(bolsas.id))
        assertEquals(20, result.storeAccount("tienda-lupita").moneyDue)
    }

    @Test
    fun `una entrega incompleta se ve negativa hasta completarla`() {
        val ledger = RouteLedger.start(
            initialCash = 0,
            initialStock = mapOf(bolsas to 15),
        )

        ledger.registerStoreSale(
            storeId = "tienda-uno",
            lines = listOf(SaleLine(product = bolsas, requested = 18, delivered = 15, total = 50)),
            amountReceived = 0,
        )

        assertEquals(-3, ledger.displayStock(bolsas.id))
        assertEquals(3, ledger.storeAccount("tienda-uno").unitsPending(bolsas.id))

        ledger.restock(mapOf(bolsas to 20))
        ledger.completeDelivery(storeId = "tienda-uno", productId = bolsas.id, units = 3, amountReceived = 50)

        assertEquals(17, ledger.availableStock(bolsas.id))
        assertEquals(0, ledger.storeAccount("tienda-uno").unitsPending(bolsas.id))
        assertEquals(50, ledger.cashOnHand)
    }

    @Test
    fun `un retornable no devuelto crea deuda de envase pero no deuda de dinero`() {
        val ledger = RouteLedger.start(
            initialCash = 0,
            initialStock = mapOf(garrafon to 10),
            initialEmptyContainers = mapOf(garrafon.id to 2),
        )

        ledger.registerStoreSale(
            storeId = "tienda-dos",
            lines = listOf(
                SaleLine(
                    product = garrafon,
                    requested = 2,
                    delivered = 2,
                    total = 16,
                    emptyContainersReceived = 1,
                ),
            ),
            amountReceived = 16,
        )

        assertEquals(1, ledger.storeAccount("tienda-dos").containersDue(garrafon.id))
        assertEquals(3, ledger.emptyContainers(garrafon.id))
        assertEquals(0, ledger.storeAccount("tienda-dos").moneyDue)
    }

    @Test
    fun `un envase vendido deja de esperarse como retornable`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(garrafon to 5))

        ledger.registerStoreSale(
            storeId = "tienda-tres",
            lines = listOf(
                SaleLine(
                    product = garrafon,
                    requested = 1,
                    delivered = 1,
                    total = 28,
                    containersSold = 1,
                ),
            ),
            amountReceived = 8,
        )

        assertEquals(0, ledger.storeAccount("tienda-tres").containersDue(garrafon.id))
        assertEquals(20, ledger.storeAccount("tienda-tres").moneyDue)
    }

    @Test
    fun `un pago parcial reduce la deuda sin mezclar ventas de fechas distintas`() {
        val ledger = RouteLedger.start(initialCash = 100, initialStock = mapOf(bolsas to 50))
        ledger.registerStoreSale(
            storeId = "tienda-cuatro",
            lines = listOf(SaleLine(bolsas, requested = 10, delivered = 10, total = 40)),
            amountReceived = 0,
        )

        ledger.collectDebt(storeId = "tienda-cuatro", amount = 15)

        assertEquals(25, ledger.storeAccount("tienda-cuatro").moneyDue)
        assertEquals(115, ledger.cashOnHand)
        assertEquals(1, ledger.storeAccount("tienda-cuatro").openDebts.size)
    }

    @Test
    fun `una venta rapida no admite fiado`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(bolsas to 10))

        val result = ledger.tryRegisterQuickSale(
            lines = listOf(SaleLine(bolsas, requested = 1, delivered = 1, total = 5)),
            amountReceived = 0,
        )

        assertFalse(result.accepted)
        assertEquals(10, ledger.availableStock(bolsas.id))
    }

    @Test
    fun `una venta rapida cobrada descuenta inventario y aumenta efectivo`() {
        val ledger = RouteLedger.start(initialCash = 20, initialStock = mapOf(bolsas to 10))

        val result = ledger.tryRegisterQuickSale(
            lines = listOf(SaleLine(bolsas, requested = 2, delivered = 2, total = 10)),
            amountReceived = 10,
        )

        assertTrue(result.accepted)
        assertEquals(8, ledger.availableStock(bolsas.id))
        assertEquals(30, ledger.cashOnHand)
    }

    @Test
    fun `una venta rapida retornable exige devolver o comprar el envase`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(garrafon to 3))

        val result = ledger.tryRegisterQuickSale(
            lines = listOf(
                SaleLine(
                    product = garrafon,
                    requested = 1,
                    delivered = 1,
                    total = 8,
                    emptyContainersReceived = 0,
                ),
            ),
            amountReceived = 8,
        )

        assertFalse(result.accepted)
        assertEquals(3, ledger.availableStock(garrafon.id))
    }

    @Test
    fun `una venta rapida puede dejar inventario negativo`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(bolsas to 1))

        val result = ledger.tryRegisterQuickSale(
            lines = listOf(SaleLine(bolsas, requested = 2, delivered = 2, total = 10)),
            amountReceived = 10,
        )

        assertTrue(result.accepted)
        assertEquals(-1, ledger.availableStock(bolsas.id))
        assertEquals(-1, ledger.displayStock(bolsas.id))
        assertEquals(10, ledger.cashOnHand)
    }

    @Test
    fun `la deuda monetaria tiene prioridad visual sobre entrega y antiguedad`() {
        val account = StoreAccount(
            storeId = "tienda-cinco",
            openDebts = mutableListOf(Debt(originalAmount = 50, remainingAmount = 50)),
            pendingDeliveries = mutableMapOf(bolsas.id to 3),
        )

        assertEquals(StoreMarkerState.DEBT, StoreMarkerPolicy.resolve(account, daysSinceLastSale = 1))
        assertTrue(account.hasPendingDelivery)
    }

    @Test
    fun `una tienda puede devolver parte de los envases que debe`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(garrafon to 5))
        ledger.registerStoreSale(
            storeId = "tienda-envases",
            lines = listOf(
                SaleLine(
                    product = garrafon,
                    requested = 3,
                    delivered = 3,
                    total = 24,
                    emptyContainersReceived = 0,
                ),
            ),
            amountReceived = 24,
        )

        ledger.receiveContainers("tienda-envases", garrafon.id, quantity = 1)

        assertEquals(2, ledger.storeAccount("tienda-envases").containersDue(garrafon.id))
        assertEquals(1, ledger.emptyContainers(garrafon.id))
    }

    @Test
    fun `cobrar completamente una deuda elimina su alerta monetaria`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(bolsas to 20))
        ledger.registerStoreSale(
            storeId = "tienda-deuda",
            lines = listOf(SaleLine(bolsas, requested = 18, delivered = 18, total = 50)),
            amountReceived = 0,
        )

        ledger.collectDebt("tienda-deuda", amount = 50)

        assertEquals(0, ledger.storeAccount("tienda-deuda").moneyDue)
        assertTrue(ledger.storeAccount("tienda-deuda").openDebts.isEmpty())
        assertEquals(50, ledger.cashOnHand)
    }

    @Test
    fun `al completar con pago parcial el saldo restante se convierte en deuda`() {
        val ledger = RouteLedger.start(initialCash = 0, initialStock = mapOf(garrafon to 8))
        ledger.registerStoreSale(
            storeId = "tienda-pendiente",
            lines = listOf(
                SaleLine(
                    product = garrafon,
                    requested = 10,
                    delivered = 8,
                    total = 80,
                    emptyContainersReceived = 8,
                ),
            ),
            amountReceived = 0,
        )

        assertEquals(80, ledger.storeAccount("tienda-pendiente").paymentPending(garrafon.id))
        ledger.restock(mapOf(garrafon to 2))
        ledger.completeDelivery("tienda-pendiente", garrafon.id, units = 2, amountReceived = 50)

        assertEquals(0, ledger.storeAccount("tienda-pendiente").unitsPending(garrafon.id))
        assertEquals(0, ledger.storeAccount("tienda-pendiente").paymentPending(garrafon.id))
        assertEquals(30, ledger.storeAccount("tienda-pendiente").moneyDue)
        assertEquals(50, ledger.cashOnHand)
    }

    @Test
    fun `una recarga en sede suma producto y descarga vacios`() {
        val ledger = RouteLedger.start(
            initialCash = 0,
            initialStock = mapOf(garrafon to 5, bolsas to 5),
            initialEmptyContainers = mapOf(garrafon.id to 8),
        )

        ledger.restock(mapOf(garrafon to 10, bolsas to 150))
        ledger.unloadEmptyContainers(garrafon.id, quantity = 6)

        assertEquals(15, ledger.availableStock(garrafon.id))
        assertEquals(155, ledger.availableStock(bolsas.id))
        assertEquals(2, ledger.emptyContainers(garrafon.id))
    }

    @Test
    fun `calcula la antiguedad de la ultima venta ignorando anuladas`() {
        val recent = SaleRecord(
            id = "recent",
            storeId = "store-color",
            lines = listOf(SaleLine(bolsas, 1, 1, 5)),
            amountReceived = 5,
            recordedAtEpochMillis = 8L * 86_400_000L,
        )
        val cancelled = recent.copy(id = "cancelled", recordedAtEpochMillis = 9L * 86_400_000L, cancelledAtEpochMillis = 10L)

        val days = StoreMarkerPolicy.daysSinceLastSale(
            "store-color",
            listOf(recent, cancelled),
            nowEpochMillis = 10L * 86_400_000L,
        )

        assertEquals(2, days)
    }

    @Test
    fun `factura con varios faltantes se cobra hasta completar el ultimo producto`() {
        val huevos = Product("eggs-pending", "Huevo")
        val ledger = RouteLedger.start(0, mapOf(bolsas to 1, huevos to 1))
        ledger.registerStoreSale(
            "store-multi",
            listOf(
                SaleLine(bolsas, requested = 2, delivered = 1, total = 20),
                SaleLine(huevos, requested = 2, delivered = 1, total = 30),
            ),
            amountReceived = 0,
        )
        ledger.restock(mapOf(bolsas to 1, huevos to 1))

        assertEquals(0, ledger.paymentDueAfterCompleting("store-multi", bolsas.id, 1))
        ledger.completeDelivery("store-multi", bolsas.id, 1, 0)
        assertEquals(50, ledger.paymentDueAfterCompleting("store-multi", huevos.id, 1))
        ledger.completeDelivery("store-multi", huevos.id, 1, 50)

        assertEquals(50, ledger.cashOnHand)
        assertEquals(0, ledger.storeAccount("store-multi").moneyDue)
    }
}
