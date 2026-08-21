package com.example.caudalapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.StoreMarkerPolicy
import com.example.caudalapp.domain.TransferDirection
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.StorePriceSuggestionPolicy
import kotlinx.coroutines.delay

@Composable
fun ActiveRouteScreen(
    route: ActiveRoute,
    stores: StoreDirectory,
    previousRoutes: List<CompletedRouteRecord> = emptyList(),
    onExitMap: () -> Unit,
    closeRequested: Boolean = false,
    onCloseRequestConsumed: () -> Unit = {},
    onRouteFinished: (RouteCloseResult) -> Unit,
    onRouteDiscarded: () -> Unit,
    onStateChanged: () -> Unit,
    onLocationPermissionGranted: () -> Unit = {},
    settings: AppSettings = AppSettings(),
    modifier: Modifier = Modifier,
) {
    var actionMenuOpen by remember { mutableStateOf(false) }
    var quickSaleOpen by remember { mutableStateOf(false) }
    var ledgerRevision by remember { mutableIntStateOf(0) }
    var storeRevision by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    var mapCenter by remember { mutableStateOf(GeoPoint(14.6349, -90.5069)) }
    var placingStore by remember { mutableStateOf(false) }
    var namingLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var movingStore by remember { mutableStateOf<Store?>(null) }
    var sellingStore by remember { mutableStateOf<Store?>(null) }
    var collectingStore by remember { mutableStateOf<Store?>(null) }
    var receivingStore by remember { mutableStateOf<Store?>(null) }
    var completingStore by remember { mutableStateOf<Store?>(null) }
    var reloadOpen by remember { mutableStateOf(false) }
    var closeOpen by remember { mutableStateOf(false) }
    var expenseOpen by remember { mutableStateOf(false) }
    var kennethTransferOpen by remember { mutableStateOf(false) }
    var accountsOpen by remember { mutableStateOf(false) }
    var startReminderShown by rememberSaveable(route.startedAtEpochMillis) { mutableStateOf(false) }
    var auditOpen by remember { mutableStateOf(false) }
    var focusLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var focusRequestId by remember { mutableIntStateOf(0) }
    var elapsedMillis by remember(route.startedAtEpochMillis) {
        mutableLongStateOf((System.currentTimeMillis() - route.startedAtEpochMillis).coerceAtLeast(0L))
    }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current.applicationContext
    val elapsedRouteId = remember(route.startedAtEpochMillis) {
        RouteTrackingService.routeId(route.startedAtEpochMillis)
    }
    val historicalSales = remember(previousRoutes, ledgerRevision) {
        previousRoutes.flatMap(CompletedRouteRecord::sales) + route.ledger.sales
    }

    LaunchedEffect(route.startedAtEpochMillis) {
        RouteTrackingService.ensureElapsedClock(context, elapsedRouteId)
        while (true) {
            elapsedMillis = RouteTrackingService.elapsedMillis(context, elapsedRouteId)
            delay(1_000L)
        }
    }

    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            closeOpen = true
            onCloseRequestConsumed()
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(1_700)
            feedback = null
        }
    }

    LaunchedEffect(route.startedAtEpochMillis) {
        if (!startReminderShown && route.ledger.outstandingAccountStates().isNotEmpty()) {
            startReminderShown = true
            accountsOpen = true
        }
    }

    if (quickSaleOpen) {
        QuickSaleDialog(
            route = route,
            location = RouteTrackingService.latestLocation(context, elapsedRouteId) ?: mapCenter,
            knownCustomers = (previousRoutes.flatMap(CompletedRouteRecord::sales) + route.ledger.sales)
                .filter { it.storeId == null && !it.customerName.isNullOrBlank() }
                .distinctBy { it.customerName!!.lowercase() },
            onDismiss = { quickSaleOpen = false },
            onSaleRegistered = { units, total ->
                quickSaleOpen = false
                ledgerRevision++
                onStateChanged()
                feedback = "−$units productos   +Q$total"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    namingLocation?.let { location ->
        StoreNameDialog(
            location = location,
            onDismiss = { namingLocation = null },
            onSave = { name, phone ->
                when (val result = stores.addStore(name, location, phone)) {
                    is StoreChangeResult.Success -> {
                        namingLocation = null
                        storeRevision++
                        onStateChanged()
                        feedback = "${result.store.name} agregada"
                        if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    is StoreChangeResult.Rejected -> Unit
                }
            },
        )
    }

    selectedStore?.let { store ->
        StoreDetailsDialog(
            store = store,
            account = route.ledger.storeAccount(store.id),
            onDismiss = { selectedStore = null },
            onNewSale = {
                selectedStore = null
                sellingStore = store
            },
            onCollectDebt = {
                selectedStore = null
                collectingStore = store
            },
            onReceiveContainers = {
                selectedStore = null
                receivingStore = store
            },
            onCompleteDelivery = {
                selectedStore = null
                completingStore = store
            },
            onMove = {
                selectedStore = null
                movingStore = store
                mapCenter = store.location
                placingStore = true
            },
        )
    }

    sellingStore?.let { store ->
        StoreSaleDialog(
            route = route,
            store = store,
            suggestedTotal = { product, quantity ->
                StorePriceSuggestionPolicy.forSale(store.id, product, quantity, historicalSales)
            },
            onDismiss = { sellingStore = null },
            onRegistered = { units, cash, pending ->
                sellingStore = null
                ledgerRevision++
                onStateChanged()
                feedback = if (pending > 0) "−$units entregados · $pending pendientes" else "−$units productos · +Q$cash"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    collectingStore?.let { store ->
        CollectDebtDialog(
            route = route,
            store = store,
            onDismiss = { collectingStore = null },
            onCollected = { amount ->
                collectingStore = null
                ledgerRevision++
                onStateChanged()
                feedback = "Cobro registrado · +Q$amount"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    receivingStore?.let { store ->
        ReceiveContainersDialog(
            route = route,
            store = store,
            onDismiss = { receivingStore = null },
            onReceived = { quantity ->
                receivingStore = null
                ledgerRevision++
                onStateChanged()
                feedback = "$quantity envases recibidos"
            },
        )
    }

    completingStore?.let { store ->
        CompleteDeliveryDialog(
            route = route,
            store = store,
            onDismiss = { completingStore = null },
            onCompleted = { units, cash ->
                completingStore = null
                ledgerRevision++
                onStateChanged()
                feedback = "$units entregados · +Q$cash"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    if (reloadOpen) {
        HeadquartersReloadDialog(
            route = route,
            onDismiss = { reloadOpen = false },
            onReloaded = { loaded, unloaded ->
                reloadOpen = false
                ledgerRevision++
                onStateChanged()
                feedback = "+$loaded producto · −$unloaded vacíos"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    if (closeOpen) {
        RouteCloseDialog(
            route = route,
            onDismiss = { closeOpen = false },
            onFinish = onRouteFinished,
            onDiscard = onRouteDiscarded,
        )
    }

    if (expenseOpen) {
        ExpenseDialog(
            route = route,
            onDismiss = { expenseOpen = false },
            onRegistered = { category, amount ->
                expenseOpen = false
                ledgerRevision++
                onStateChanged()
                feedback = "${category.displayName} · −Q$amount"
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    if (kennethTransferOpen) {
        KennethTransferDialog(
            route = route,
            onDismiss = { kennethTransferOpen = false },
            onTransferred = { direction, units ->
                kennethTransferOpen = false
                ledgerRevision++
                onStateChanged()
                feedback = if (direction == TransferDirection.TO_KENNETH) {
                    "$units unidades entregadas a Kenneth"
                } else {
                    "$units unidades recibidas de Kenneth"
                }
                if (settings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    if (accountsOpen) {
        key(ledgerRevision) {
            PendingAccountsDialog(
                stores = stores,
                accounts = route.ledger.outstandingAccountStates(),
                productName = { id -> route.ledger.products().firstOrNull { it.id == id }?.name ?: id },
                onDismiss = { accountsOpen = false },
                onSelectStore = { store ->
                    accountsOpen = false
                    focusLocation = store.location
                    focusRequestId++
                },
            )
        }
    }

    if (auditOpen) {
        key(ledgerRevision) {
            ActiveRouteAuditDialog(
                route = route,
                stores = stores,
                onDismiss = { auditOpen = false },
                onChanged = { message ->
                    ledgerRevision++
                    onStateChanged()
                    feedback = message
                },
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        StoreMapLayer(
            stores = stores.activeStores(),
            center = mapCenter,
            placementMode = placingStore,
            onCenterChanged = { mapCenter = it },
            onStoreSelected = { selectedStore = it },
            markerState = { store ->
                StoreMarkerPolicy.resolve(
                    route.ledger.storeAccount(store.id),
                    daysSinceLastSale = StoreMarkerPolicy.daysSinceLastSale(store.id, historicalSales),
                )
            },
            moneyDue = { store -> route.ledger.storeAccount(store.id).moneyDue },
            unitsPending = { store ->
                route.ledger.products().sumOf { route.ledger.storeAccount(store.id).unitsPending(it.id) }
            },
            focusLocation = focusLocation,
            focusRequestId = focusRequestId,
            onLocationPermissionGranted = onLocationPermissionGranted,
            automaticFollow = settings.automaticMapFollow,
            gpsIntervalSeconds = settings.gpsIntervalSeconds,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Text(route.name, fontWeight = FontWeight.Bold)
                    Text(formatRouteDuration(elapsedMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { closeOpen = true }) {
                    Text("Finalizar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onExitMap) {
                    Text("Salir del mapa", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!placingStore && landscape) {
            key(ledgerRevision) {
                RouteSummaryPanel(
                    route = route,
                    expanded = panelExpanded,
                    onToggle = { panelExpanded = !panelExpanded },
                    onAccountsClick = { accountsOpen = true },
                    onAuditClick = { auditOpen = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 82.dp)
                        .width(if (panelExpanded) 270.dp else 200.dp),
                )
            }
        } else if (!placingStore) {
            key(ledgerRevision) {
                RouteSummaryPanel(
                    route = route,
                    expanded = panelExpanded,
                    onToggle = { panelExpanded = !panelExpanded },
                    onAccountsClick = { accountsOpen = true },
                    onAuditClick = { auditOpen = true },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }

        AnimatedVisibility(
            visible = actionMenuOpen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    bottom = if (landscape) 92.dp else if (panelExpanded) 282.dp else 164.dp,
                ),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteActionButton("Venta rápida") {
                    actionMenuOpen = false
                    quickSaleOpen = true
                }
                RouteActionButton("Agregar tienda") {
                    actionMenuOpen = false
                    movingStore = null
                    placingStore = true
                }
                RouteActionButton("Recargar en sede") {
                    actionMenuOpen = false
                    reloadOpen = true
                }
                RouteActionButton("Transferir a Kenneth") {
                    actionMenuOpen = false
                    kennethTransferOpen = true
                }
                RouteActionButton("Registrar gasto") {
                    actionMenuOpen = false
                    expenseOpen = true
                }
            }
        }

        val rotation by animateFloatAsState(if (actionMenuOpen) 45f else 0f, label = "fabRotation")
        Button(
            onClick = { actionMenuOpen = !actionMenuOpen },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    bottom = if (landscape) 20.dp else if (panelExpanded) 210.dp else 92.dp,
                )
                .size(60.dp)
                .graphicsLayer { rotationZ = rotation },
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("+", fontSize = 30.sp)
        }

        if (placingStore) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = {
                        val storeToMove = movingStore
                        if (storeToMove == null) {
                            namingLocation = mapCenter
                        } else {
                            stores.moveStore(storeToMove.id, mapCenter)
                            storeRevision++
                            onStateChanged()
                            feedback = "Ubicación actualizada"
                            movingStore = null
                        }
                        placingStore = false
                    }) { Text(if (movingStore == null) "Usar este punto" else "Guardar ubicación") }
                    Button(onClick = {
                        placingStore = false
                        movingStore = null
                    }) { Text("Cancelar") }
                }
            }
        }

        feedback?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 88.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatRouteDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
private fun RouteActionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Text(label, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun RouteSummaryPanel(
    route: ActiveRoute,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAccountsClick: () -> Unit,
    onAuditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(12.dp).animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efectivo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Q${route.ledger.cashOnHand}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    route.ledger.products().filterNot { it.archived }.forEach { product ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(product.name)
                            Text(route.ledger.displayStock(product.id).toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onAccountsClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Cuentas pendientes →", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onAuditClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Movimientos (${route.ledger.auditEntries.size}) →", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Ocultar ▲" else "Ver inventario ▼")
            }
        }
    }
}
