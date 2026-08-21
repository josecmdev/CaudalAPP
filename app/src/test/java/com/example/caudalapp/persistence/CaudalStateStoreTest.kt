package com.example.caudalapp.persistence

import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.TransferDirection
import com.example.caudalapp.domain.TransferLine
import com.example.caudalapp.domain.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaudalStateStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `serializa y recupera ruta activa tiendas cuentas e historial`() {
        val bags = ProductCatalog.BAGS
        val ledger = RouteLedger.start(initialCash = 200, initialStock = mapOf(bags to 15))
        val stores = StoreDirectory { "store-persisted" }
        val store = (stores.addStore("Tienda Azul", GeoPoint(14.63, -90.50)) as StoreChangeResult.Success).store
        ledger.registerStoreSale(
            storeId = store.id,
            lines = listOf(SaleLine(bags, requested = 18, delivered = 15, total = 50)),
            amountReceived = 0,
            location = store.location,
        )
        ledger.transferWithKenneth(
            TransferDirection.FROM_KENNETH,
            listOf(TransferLine(ProductCatalog.EGGS, fullUnits = 4)),
            recordedAtEpochMillis = 999L,
        )
        ledger.registerExpense(ExpenseCategory.REFRESHMENT, 10)
        ledger.cancelExpense(ledger.expenses.single().id, "Corrección")
        val active = ActiveRoute("Viernes", ledger, startedAtEpochMillis = 1234L)
        val completed = CompletedRouteRecord(
            "Martes",
            10L,
            20L,
            ledger.closeSummary(),
            trackId = "10",
            trackPointCount = 321,
            sales = ledger.sales,
            auditEntries = ledger.auditEntries,
            activeDurationMillis = 180_000L,
            countedCash = 250,
            countedStock = mapOf(bags.id to 12),
        )
        val products = ProductDirectory()
        products.addProduct("Refresco", 12, false, "content://refresco")

        val decoded = CaudalStateCodec.decode(
            CaudalStateCodec.encode(
                CaudalRestoredState(
                    active,
                    stores,
                    listOf(completed),
                    outstandingAccounts = ledger.outstandingAccountStates(),
                    products = products,
                ),
            ),
        )

        assertEquals("Viernes", decoded.activeRoute?.name)
        assertEquals(-3, decoded.activeRoute?.ledger?.displayStock(bags.id))
        assertEquals(3, decoded.activeRoute?.ledger?.storeAccount(store.id)?.unitsPending(bags.id))
        assertEquals("Tienda Azul", decoded.stores.get(store.id)?.name)
        assertEquals(4, decoded.activeRoute?.ledger?.availableStock(ProductCatalog.EGGS.id))
        assertEquals(1, decoded.activeRoute?.ledger?.kennethTransfers?.size)
        assertEquals(1, decoded.completedRoutes.size)
        assertEquals("10", decoded.completedRoutes.single().trackId)
        assertEquals(321, decoded.completedRoutes.single().trackPointCount)
        assertEquals(1, decoded.activeRoute?.ledger?.sales?.size)
        assertEquals(store.location, decoded.activeRoute?.ledger?.sales?.single()?.location)
        assertEquals(4, decoded.activeRoute?.ledger?.auditEntries?.size)
        assertEquals(true, decoded.activeRoute?.ledger?.expenses?.single()?.cancelled)
        assertEquals(1, decoded.completedRoutes.single().sales.size)
        assertEquals(180_000L, decoded.completedRoutes.single().activeDurationMillis)
        assertEquals(250, decoded.completedRoutes.single().countedCash)
        assertNotNull(decoded.products.allProducts().firstOrNull { it.name == "Refresco" })
        assertEquals(3, decoded.outstandingAccounts.single().pendingDeliveries.getValue(bags.id))
    }

    @Test
    fun `el archivo local conserva el estado y un archivo corrupto no bloquea el inicio`() {
        val file = temporaryFolder.newFile("caudal-state.json")
        val store = CaudalStateStore(file)
        val state = CaudalRestoredState(activeRoute = null, stores = StoreDirectory(), completedRoutes = emptyList())

        store.save(state)
        assertNotNull(store.load())

        file.writeText("no-es-json")
        assertNull(store.load())
    }
}
