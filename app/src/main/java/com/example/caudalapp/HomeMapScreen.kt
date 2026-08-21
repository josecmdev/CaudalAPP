package com.example.caudalapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caudalapp.domain.AccountSettlementPolicy
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.domain.StoreAccountAdjustmentType
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.StoreMarkerPolicy
import com.example.caudalapp.domain.StoreMarkerState

@Composable
fun HomeMapScreen(
    stores: StoreDirectory,
    accounts: List<StoreAccountState>,
    previousRoutes: List<CompletedRouteRecord>,
    onStartRoute: () -> Unit,
    onExitMap: () -> Unit,
    onStateChanged: () -> Unit,
    onAccountsChanged: (List<StoreAccountState>, StoreAccountAdjustment) -> Unit,
    productName: (String) -> String,
    settings: AppSettings = AppSettings(),
    modifier: Modifier = Modifier,
) {
    var revision by remember { mutableIntStateOf(0) }
    var center by remember { mutableStateOf(GeoPoint(14.6349, -90.5069)) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var managingAccountStore by remember { mutableStateOf<Store?>(null) }
    var placementMode by remember { mutableStateOf(false) }
    var namingLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    val accountByStore = remember(accounts) { accounts.associateBy(StoreAccountState::storeId) }
    val historicalSales = remember(previousRoutes) { previousRoutes.flatMap(CompletedRouteRecord::sales) }
    val displayedStores = remember(revision) { stores.activeStores() }

    namingLocation?.let { location ->
        StoreNameDialog(
            location = location,
            onDismiss = { namingLocation = null },
            onSave = { name, phone ->
                if (stores.addStore(name, location, phone) is StoreChangeResult.Success) {
                    namingLocation = null
                    revision++
                    onStateChanged()
                }
            },
        )
    }

    selectedStore?.let { store ->
        val account = accountByStore[store.id]
        HomeStoreDialog(
            store = store,
            account = account,
            onDismiss = { selectedStore = null },
            onManageAccount = account?.takeIf(AccountSettlementPolicy::hasPending)?.let {
                { selectedStore = null; managingAccountStore = store }
            },
        )
    }

    managingAccountStore?.let { store ->
        val account = accountByStore[store.id]
        if (account == null) managingAccountStore = null else {
            StoreAccountManagementDialog(
                store = store,
                account = account,
                productName = productName,
                onDismiss = { managingAccountStore = null },
                onPayment = { amount ->
                    onAccountsChanged(
                        accounts.replaceHomeAccount(AccountSettlementPolicy.registerPayment(account, amount)),
                        StoreAccountAdjustment(storeId = store.id, type = StoreAccountAdjustmentType.MONEY_PAYMENT, amount = amount),
                    )
                    managingAccountStore = null
                },
                onDeliveryCompleted = { productId, quantity ->
                    onAccountsChanged(
                        accounts.replaceHomeAccount(AccountSettlementPolicy.completeDelivery(account, productId, quantity)),
                        StoreAccountAdjustment(storeId = store.id, type = StoreAccountAdjustmentType.DELIVERY_COMPLETED, productId = productId, quantity = quantity),
                    )
                    managingAccountStore = null
                },
                onContainersReceived = { productId, quantity ->
                    onAccountsChanged(
                        accounts.replaceHomeAccount(AccountSettlementPolicy.receiveContainers(account, productId, quantity)),
                        StoreAccountAdjustment(storeId = store.id, type = StoreAccountAdjustmentType.CONTAINER_RETURN, productId = productId, quantity = quantity),
                    )
                    managingAccountStore = null
                },
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        StoreMapLayer(
            stores = displayedStores,
            center = center,
            placementMode = placementMode,
            onCenterChanged = { center = it },
            onStoreSelected = { selectedStore = it },
            markerState = { store ->
                val account = accountByStore[store.id]
                when {
                    account.moneyDue() > 0 -> StoreMarkerState.DEBT
                    account.unitsPending() > 0 -> StoreMarkerState.PENDING_DELIVERY
                    else -> when (val days = StoreMarkerPolicy.daysSinceLastSale(store.id, historicalSales)) {
                        in 0..3 -> StoreMarkerState.RECENT
                        in 4..7 -> StoreMarkerState.DUE_SOON
                        else -> StoreMarkerState.INACTIVE
                    }
                }
            },
            moneyDue = { store -> accountByStore[store.id].moneyDue() },
            unitsPending = { store -> accountByStore[store.id].unitsPending() },
            automaticFollow = settings.automaticMapFollow,
            gpsIntervalSeconds = settings.gpsIntervalSeconds,
            mapStyle = settings.mapStyle,
            modifier = Modifier.fillMaxSize(),
        )

        Button(
            onClick = onExitMap,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Salir del mapa", fontWeight = FontWeight.Bold) }

        if (placementMode) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { placementMode = false }) { Text("Cancelar") }
                Button(onClick = { placementMode = false; namingLocation = center }) { Text("Usar este punto") }
            }
        } else {
            val controlsBottom = if (!landscape && panelExpanded) 224.dp else 62.dp
            Button(
                onClick = { placementMode = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = controlsBottom).size(58.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("+", fontSize = 28.sp) }
            Button(
                onClick = onStartRoute,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = controlsBottom),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Iniciar ruta", modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            HomeMapPanel(
                storeCount = displayedStores.size,
                pendingCount = accounts.count(AccountSettlementPolicy::hasPending),
                expanded = panelExpanded,
                landscape = landscape,
                onExpandedChange = { panelExpanded = it },
                modifier = if (landscape) {
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 76.dp, bottom = 12.dp)
                        .width(if (panelExpanded) 270.dp else 42.dp)
                } else Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeStoreDialog(store: Store, account: StoreAccountState?, onDismiss: () -> Unit, onManageAccount: (() -> Unit)?) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(store.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                store.phone?.let { Text("Teléfono: $it") }
                Text("${"%.5f".format(store.location.latitude)}, ${"%.5f".format(store.location.longitude)}")
                if (account == null || !AccountSettlementPolicy.hasPending(account)) {
                    Text("Sin cuentas pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "Debe Q${AccountSettlementPolicy.moneyDue(account)} · ${account.pendingContainers.values.sum()} envases · ${account.pendingDeliveries.values.sum()} productos",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(onClick = requireNotNull(onManageAccount), modifier = Modifier.fillMaxWidth()) {
                        Text("Gestionar pagos y pendientes")
                    }
                }
                Text("Las ventas nuevas requieren iniciar una ruta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun HomeMapPanel(
    storeCount: Int,
    pendingCount: Int,
    expanded: Boolean,
    landscape: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    Card(
        modifier = modifier.pointerInput(landscape, expanded) {
            detectDragGestures(
                onDragStart = { drag = Offset.Zero },
                onDragEnd = {
                    if (landscape) {
                        if (drag.x < -35f) onExpandedChange(true) else if (drag.x > 35f) onExpandedChange(false)
                    } else {
                        if (drag.y < -35f) onExpandedChange(true) else if (drag.y > 35f) onExpandedChange(false)
                    }
                },
                onDrag = { change, amount -> change.consume(); drag += amount },
            )
        },
        shape = RoundedCornerShape(if (landscape) 24.dp else 26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (expanded) 18.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(if (landscape) if (expanded) "›" else "‹" else if (expanded) "Ocultar ▼" else "Información ▲")
            }
            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mapa de tiendas", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tiendas"); Text(storeCount.toString(), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cuentas pendientes")
                        Text(pendingCount.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text("Toca una tienda para registrar pagos, envases o entregas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun List<StoreAccountState>.replaceHomeAccount(updated: StoreAccountState): List<StoreAccountState> =
    mapNotNull { current -> if (current.storeId != updated.storeId) current else updated.takeIf(AccountSettlementPolicy::hasPending) }

private fun StoreAccountState?.moneyDue(): Int = this?.let(AccountSettlementPolicy::moneyDue) ?: 0
private fun StoreAccountState?.unitsPending(): Int = this?.pendingDeliveries?.values?.sum() ?: 0
