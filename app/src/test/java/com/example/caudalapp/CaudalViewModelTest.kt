package com.example.caudalapp

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.example.caudalapp.domain.SaleLine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.AccountSettlementPolicy
import com.example.caudalapp.domain.Debt
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.domain.StoreAccountAdjustmentType
import com.example.caudalapp.domain.StoreAccountState

class CaudalViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `android puede crear el viewmodel mediante su constructor publico`() {
        val constructor = CaudalViewModel::class.java.getDeclaredConstructor()

        assertTrue(Modifier.isPublic(CaudalViewModel::class.java.modifiers))
        assertTrue(Modifier.isPublic(constructor.modifiers))
        assertNotNull(constructor.newInstance())
        assertTrue(constructor.newInstance().mapVisible)
    }

    @Test
    fun `un viewmodel nuevo recupera la ruta activa guardada localmente`() {
        val file = temporaryFolder.newFile("state.json")
        val first = CaudalViewModel()
        first.attachPersistence(file)
        first.activeRoute = ActiveRoute(
            name = "Viernes",
            ledger = RouteLedger.start(100, mapOf(ProductCatalog.BAGS to 20)),
            startedAtEpochMillis = 55L,
        )
        first.persist()

        val restored = CaudalViewModel()
        restored.attachPersistence(file)

        assertNotNull(restored.activeRoute)
        assertTrue(restored.activeRoute?.name == "Viernes")
        assertTrue(restored.activeRoute?.ledger?.availableStock(ProductCatalog.BAGS.id) == 20)
    }

    @Test
    fun `finalizar ruta la pasa al historial con sus cuentas pendientes`() {
        val viewModel = CaudalViewModel()
        viewModel.startRoute(ActiveRoute("Lunes", RouteLedger.start(100, mapOf(ProductCatalog.BAGS to 20)), 5L))
        val ledger = viewModel.activeRoute!!.ledger
        ledger.registerStoreSale("tienda", listOf(SaleLine(ProductCatalog.BAGS, 8, 8, 25)), amountReceived = 5)

        viewModel.finishRoute(RouteCloseResult(ledger.closeSummary()), "5", 60_000L, 12, finishedAtEpochMillis = 9L)

        val record = viewModel.completedRoutes.single()
        assertNull(viewModel.activeRoute)
        assertEquals(1, record.sales.size)
        assertEquals(12, record.expectedStock.getValue(ProductCatalog.BAGS.id))
        assertEquals(20, AccountSettlementPolicy.moneyDue(viewModel.outstandingAccounts.single()))
    }

    @Test
    fun `descartar ruta deja las cuentas como estaban antes de iniciarla`() {
        val viewModel = CaudalViewModel()
        val before = listOf(StoreAccountState("tienda", listOf(Debt(50, 50)), emptyMap(), emptyMap(), emptyMap()))
        viewModel.outstandingAccounts = before
        viewModel.startRoute(ActiveRoute("Lunes", RouteLedger.start(0, mapOf(ProductCatalog.BAGS to 20)), 5L))
        viewModel.activeRoute!!.ledger.collectDebt("tienda", 50)

        viewModel.discardRoute()

        assertNull(viewModel.activeRoute)
        assertTrue(viewModel.completedRoutes.isEmpty())
        assertEquals(before, viewModel.currentAccounts)
    }

    @Test
    fun `un cobro desde Tiendas con ruta activa queda en las cuentas de la ruta`() {
        val viewModel = CaudalViewModel()
        viewModel.outstandingAccounts = listOf(
            StoreAccountState("tienda", listOf(Debt(200, 200)), emptyMap(), emptyMap(), mapOf("bolsas" to 50)),
        )
        val ledger = RouteLedger.start(100, mapOf(ProductCatalog.BAGS to 20))
        ledger.importAccounts(viewModel.outstandingAccounts)
        viewModel.activeRoute = ActiveRoute("Viernes", ledger, 1L)

        viewModel.applyAccountChange(
            emptyList(),
            StoreAccountAdjustment(storeId = "tienda", type = StoreAccountAdjustmentType.MONEY_PAYMENT, amount = 220),
        )

        val account = viewModel.currentAccounts.single()
        assertEquals(30, AccountSettlementPolicy.moneyDue(account))
        assertEquals(320, ledger.cashOnHand)
        assertEquals(account, ledger.outstandingAccountStates().single())
    }
}
