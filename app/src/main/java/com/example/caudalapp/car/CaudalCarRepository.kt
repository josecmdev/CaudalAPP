package com.example.caudalapp.car

import com.example.caudalapp.domain.AccountSettlementPolicy
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.domain.StoreAccountAdjustmentType
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.SuggestedPricePolicy
import com.example.caudalapp.persistence.CaudalRestoredState
import com.example.caudalapp.persistence.CaudalStateStore
import java.io.File

data class CarStoreSnapshot(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val moneyDue: Int,
    val containersDue: Int,
    val unitsPending: Int,
)

data class CaudalCarSnapshot(
    val routeName: String?,
    val routeStartedAtEpochMillis: Long?,
    val bagStock: Int,
    val stores: List<CarStoreSnapshot>,
)

sealed interface CarOperationResult {
    data class Success(val message: String) : CarOperationResult
    data class Rejected(val message: String) : CarOperationResult
}

object CarBagPresetPolicy {
    val quantities = listOf(8, 18, 36, 54, 72)

    fun totalFor(quantity: Int): Int? = SuggestedPricePolicy.forQuantity(ProductCatalog.BAGS, quantity)
}

class CaudalCarRepository(private val stateFile: File) {
    private val stateStore = CaudalStateStore(stateFile)

    fun snapshot(): CaudalCarSnapshot = synchronized(FILE_ACCESS) {
        val state = stateStore.load()
        if (state == null) return@synchronized CaudalCarSnapshot(null, null, 0, emptyList())
        val activeAccounts = state.activeRoute?.ledger?.outstandingAccountStates()?.associateBy(StoreAccountState::storeId)
        val savedAccounts = state.outstandingAccounts.associateBy(StoreAccountState::storeId)
        CaudalCarSnapshot(
            routeName = state.activeRoute?.name,
            routeStartedAtEpochMillis = state.activeRoute?.startedAtEpochMillis,
            bagStock = state.activeRoute?.ledger?.availableStock(ProductCatalog.BAGS.id) ?: 0,
            stores = state.stores.activeStores().map { store ->
                val account = activeAccounts?.get(store.id) ?: savedAccounts[store.id]
                CarStoreSnapshot(
                    id = store.id,
                    name = store.name,
                    latitude = store.location.latitude,
                    longitude = store.location.longitude,
                    moneyDue = account?.let(AccountSettlementPolicy::moneyDue) ?: 0,
                    containersDue = account?.pendingContainers?.values?.sum() ?: 0,
                    unitsPending = account?.pendingDeliveries?.values?.sum() ?: 0,
                )
            },
        )
    }

    fun sellBags(storeId: String, quantity: Int): CarOperationResult = update { state ->
        val route = state.activeRoute
            ?: return@update CarOperationResult.Rejected("Inicia una ruta en la tablet antes de vender")
        val store = state.stores.get(storeId)
            ?: return@update CarOperationResult.Rejected("La tienda ya no existe")
        val total = CarBagPresetPolicy.totalFor(quantity)
            ?: return@update CarOperationResult.Rejected("La cantidad no tiene un precio rápido")
        val available = route.ledger.availableStock(ProductCatalog.BAGS.id)
        if (available < quantity) {
            return@update CarOperationResult.Rejected("Solo hay $available bolsas. Completa esta venta en la tablet")
        }
        val product = route.ledger.products().firstOrNull { it.id == ProductCatalog.BAGS.id }
            ?: state.products.get(ProductCatalog.BAGS.id)
            ?: ProductCatalog.BAGS
        route.ledger.registerStoreSale(
            storeId = store.id,
            lines = listOf(SaleLine(product, requested = quantity, delivered = quantity, total = total)),
            amountReceived = total,
            location = store.location,
        )
        CarOperationResult.Success("Venta registrada: $quantity bolsas · Q$total")
    }

    fun collectFullDebt(storeId: String): CarOperationResult = update { state ->
        val route = state.activeRoute
        if (route != null) {
            val due = route.ledger.outstandingAccountStates()
                .firstOrNull { it.storeId == storeId }
                ?.let(AccountSettlementPolicy::moneyDue) ?: 0
            if (due <= 0) return@update CarOperationResult.Rejected("Esta tienda no tiene deuda")
            route.ledger.collectDebt(storeId, due)
            return@update CarOperationResult.Success("Cobro completo registrado: Q$due")
        }

        val account = state.outstandingAccounts.firstOrNull { it.storeId == storeId }
            ?: return@update CarOperationResult.Rejected("Esta tienda no tiene deuda")
        val due = AccountSettlementPolicy.moneyDue(account)
        if (due <= 0) return@update CarOperationResult.Rejected("Esta tienda no tiene deuda")
        val updated = AccountSettlementPolicy.registerPayment(account, due)
        state.outstandingAccounts = state.outstandingAccounts.replaceAccount(updated)
        state.accountAdjustments += StoreAccountAdjustment(
            storeId = storeId,
            type = StoreAccountAdjustmentType.MONEY_PAYMENT,
            amount = due,
        )
        CarOperationResult.Success("Cobro completo registrado: Q$due")
    }

    private fun update(operation: (MutableCarState) -> CarOperationResult): CarOperationResult = synchronized(FILE_ACCESS) {
        val restored = stateStore.load()
            ?: return@synchronized CarOperationResult.Rejected("Abre Caudal App al menos una vez en este dispositivo")
        val mutable = MutableCarState(restored)
        val result = runCatching { operation(mutable) }
            .getOrElse { CarOperationResult.Rejected(it.message ?: "No se pudo registrar la operación") }
        if (result is CarOperationResult.Success) stateStore.save(mutable.toRestoredState())
        result
    }

    private companion object {
        val FILE_ACCESS = Any()
    }
}

private class MutableCarState(restored: CaudalRestoredState) {
    var activeRoute = restored.activeRoute
    val stores = restored.stores
    val completedRoutes = restored.completedRoutes
    var outstandingAccounts = restored.outstandingAccounts
    val products = restored.products
    val accountAdjustments = restored.accountAdjustments.toMutableList()

    fun toRestoredState() = CaudalRestoredState(
        activeRoute = activeRoute,
        stores = stores,
        completedRoutes = completedRoutes,
        outstandingAccounts = outstandingAccounts,
        products = products,
        accountAdjustments = accountAdjustments,
    )
}

private fun List<StoreAccountState>.replaceAccount(updated: StoreAccountState): List<StoreAccountState> =
    mapNotNull { current ->
        if (current.storeId != updated.storeId) current else updated.takeIf(AccountSettlementPolicy::hasPending)
    }
