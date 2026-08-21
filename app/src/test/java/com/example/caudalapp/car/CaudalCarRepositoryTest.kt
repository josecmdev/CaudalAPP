package com.example.caudalapp.car

import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Debt
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.persistence.CaudalRestoredState
import com.example.caudalapp.persistence.CaudalStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaudalCarRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `venta estacionada usa el precio habitual y actualiza la ruta`() {
        val file = temporaryFolder.newFile("state.json")
        val stores = StoreDirectory()
        val store = (stores.addStore("Lupita", GeoPoint(14.63, -90.50)) as StoreChangeResult.Success).store
        val ledger = RouteLedger.start(0, mapOf(ProductCatalog.BAGS to 100))
        ledger.importAccounts(
            listOf(StoreAccountState(store.id, listOf(Debt(50, 50)), emptyMap(), emptyMap(), emptyMap())),
        )
        CaudalStateStore(file).save(
            CaudalRestoredState(ActiveRoute("Viernes", ledger), stores, emptyList(), products = ProductDirectory()),
        )

        val repository = CaudalCarRepository(file)
        val sale = repository.sellBags(store.id, 36)
        val collection = repository.collectFullDebt(store.id)
        val restored = requireNotNull(CaudalStateStore(file).load()).activeRoute!!.ledger

        assertTrue(sale is CarOperationResult.Success)
        assertTrue(collection is CarOperationResult.Success)
        assertEquals(64, restored.availableStock(ProductCatalog.BAGS.id))
        assertEquals(150, restored.cashOnHand)
        assertEquals(0, restored.storeAccount(store.id).moneyDue)
    }

    @Test
    fun `cobro sin ruta actualiza la cuenta persistida`() {
        val file = temporaryFolder.newFile("state.json")
        val stores = StoreDirectory()
        val store = (stores.addStore("Lupita", GeoPoint(14.63, -90.50)) as StoreChangeResult.Success).store
        val account = StoreAccountState(store.id, listOf(Debt(50, 50)), emptyMap(), emptyMap(), emptyMap())
        CaudalStateStore(file).save(
            CaudalRestoredState(null, stores, emptyList(), outstandingAccounts = listOf(account)),
        )

        val result = CaudalCarRepository(file).collectFullDebt(store.id)
        val restored = requireNotNull(CaudalStateStore(file).load())

        assertTrue(result is CarOperationResult.Success)
        assertTrue(restored.outstandingAccounts.isEmpty())
        assertEquals(1, restored.accountAdjustments.size)
        assertEquals(50, restored.accountAdjustments.single().amount)
    }

    @Test
    fun `cantidades rapidas incluyen ocho por veinticinco y dieciocho por cincuenta`() {
        assertEquals(listOf(8, 18, 36, 54, 72), CarBagPresetPolicy.quantities)
        assertEquals(listOf(25, 50, 100, 150, 200), CarBagPresetPolicy.quantities.map(CarBagPresetPolicy::totalFor))
    }
}
