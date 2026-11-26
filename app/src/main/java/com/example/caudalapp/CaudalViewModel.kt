package com.example.caudalapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.persistence.CaudalRestoredState
import com.example.caudalapp.persistence.CaudalStateStore
import com.example.caudalapp.persistence.CaudalStateWriter
import com.example.caudalapp.persistence.detachedCopy
import com.example.caudalapp.sync.CaudalSync
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
class CaudalViewModel : ViewModel() {
    var destination by mutableStateOf<HomeDestination?>(null)
    var mapVisible by mutableStateOf(true)
    var routeCloseRequested by mutableStateOf(false)
    private val completionTransitions = Channel<Unit>(capacity = Channel.BUFFERED)
    var activeRoute by mutableStateOf<ActiveRoute?>(null)
    var stores by mutableStateOf(StoreDirectory())
        private set
    val completedRoutes = mutableStateListOf<CompletedRouteRecord>()
    var outstandingAccounts by mutableStateOf<List<StoreAccountState>>(emptyList())
    val accountAdjustments = mutableStateListOf<StoreAccountAdjustment>()
    var products by mutableStateOf(ProductDirectory())
        private set
    var storeFocusId by mutableStateOf<String?>(null)
    private var stateStore: CaudalStateStore? = null
    var settings by mutableStateOf(AppSettings())
        private set
    private var settingsStore: AppSettingsStore? = null

    private val externalChangeListener: () -> Unit = {
        android.os.Handler(android.os.Looper.getMainLooper()).post(::reloadPersistence)
    }

    fun attachPersistence(file: File) {
        if (stateStore != null) return
        stateStore = CaudalStateStore(file)
        CaudalStateWriter.addExternalChangeListener(externalChangeListener)
        reloadPersistence()
        CaudalSync.sharedStoresListener = { remote ->
            if (stores.mergeShared(remote)) {
                stores = StoreDirectory.restore(stores.allStores())
                persist()
            }
        }
    }

    fun reloadPersistence() {
        val store = stateStore ?: return
        CaudalStateWriter.runBlocking { store.load() }?.let(::applyRestoredState)
    }

    override fun onCleared() {
        CaudalStateWriter.removeExternalChangeListener(externalChangeListener)
        CaudalSync.sharedStoresListener = null
    }

    private fun applyRestoredState(restored: CaudalRestoredState) {
        activeRoute = restored.activeRoute
        stores = restored.stores
        completedRoutes.clear()
        completedRoutes.addAll(restored.completedRoutes)
        outstandingAccounts = restored.outstandingAccounts
        accountAdjustments.clear()
        accountAdjustments.addAll(restored.accountAdjustments)
        products = restored.products
    }

    fun applyBackup(restored: CaudalRestoredState) {
        applyRestoredState(restored)
        storeFocusId = null
    }

    /** Guarda en segundo plano para no congelar la pantalla en cada venta. */
    fun persist() {
        val store = stateStore ?: return
        val snapshot = currentState().detachedCopy()
        CaudalStateWriter.enqueue { store.save(snapshot) }
    }

    /** Guarda y espera; para cuando la app pasa a segundo plano y Android podría cerrarla. */
    fun persistNow() {
        val store = stateStore ?: return
        val snapshot = currentState()
        CaudalStateWriter.runBlocking { store.save(snapshot) }
    }

    private fun currentState() = CaudalRestoredState(
        activeRoute = activeRoute,
        stores = stores,
        completedRoutes = completedRoutes.toList(),
        outstandingAccounts = outstandingAccounts,
        products = products,
        accountAdjustments = accountAdjustments.toList(),
    )

    private var accountsRevision by mutableIntStateOf(0)

    /** Con ruta activa, las cuentas vivas son las de la ruta; las guardadas se reemplazan al cerrarla. */
    val currentAccounts: List<StoreAccountState>
        get() {
            accountsRevision
            return activeRoute?.ledger?.outstandingAccountStates() ?: outstandingAccounts
        }

    fun applyAccountChange(updated: List<StoreAccountState>, adjustment: StoreAccountAdjustment) {
        val route = activeRoute
        if (route == null) outstandingAccounts = updated else route.ledger.applyAccountAdjustment(adjustment)
        accountAdjustments += adjustment
        accountsRevision++
        persist()
    }

    fun attachSettings(context: android.content.Context) {
        if (settingsStore != null) return
        settingsStore = AppSettingsStore(context)
        settings = settingsStore!!.load()
    }

    fun updateSettings(updated: AppSettings) {
        settings = updated
        settingsStore?.save(updated)
    }

    fun showCompletionTransition() {
        completionTransitions.trySend(Unit)
    }

    fun completionTransitionEvents() = completionTransitions.receiveAsFlow()

    fun startRoute(route: ActiveRoute) {
        // Una sola descarga por ruta: trae las tiendas que otras tabletas agregaron o cambiaron.
        CaudalSync.pullSharedStores()
        route.ledger.registerProducts(products.allProducts())
        route.ledger.importAccounts(outstandingAccounts)
        activeRoute = route
        destination = null
        mapVisible = true
        persist()
    }

    /** Pasa la ruta al historial; sus cuentas quedan como las cuentas pendientes de la siguiente. */
    fun finishRoute(
        closeResult: RouteCloseResult,
        trackId: String,
        activeDurationMillis: Long,
        trackPointCount: Int,
        finishedAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val route = activeRoute ?: return
        completedRoutes += CompletedRouteRecord(
            routeName = route.name,
            startedAtEpochMillis = route.startedAtEpochMillis,
            finishedAtEpochMillis = finishedAtEpochMillis,
            summary = closeResult.summary,
            trackId = trackId,
            trackPointCount = trackPointCount,
            sales = route.ledger.sales,
            auditEntries = route.ledger.auditEntries,
            activeDurationMillis = activeDurationMillis,
            countedCash = closeResult.countedCash,
            countedStock = closeResult.countedStock,
            expectedStock = route.ledger.products().associate { it.id to route.ledger.availableStock(it.id) },
        )
        outstandingAccounts = route.ledger.outstandingAccountStates()
        leaveRoute()
    }

    /** Descarta la ruta: las cuentas pendientes vuelven a como estaban antes de iniciarla. */
    fun discardRoute() {
        if (activeRoute == null) return
        leaveRoute()
    }

    private fun leaveRoute() {
        activeRoute = null
        destination = null
        mapVisible = true
        accountsRevision++
        showCompletionTransition()
        persist()
    }

    fun setRouteArchived(record: CompletedRouteRecord, archived: Boolean) {
        val index = completedRoutes.indexOf(record)
        if (index < 0) return
        completedRoutes[index] = record.copy(archived = archived)
        persist()
    }
}