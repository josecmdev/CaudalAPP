package com.example.caudalapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.example.caudalapp.domain.AccountSettlementPolicy
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
    var pendingNoticeVisible by remember { mutableStateOf(false) }
    var auditOpen by remember { mutableStateOf(false) }
    var focusLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var focusRequestId by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current.applicationContext
    val elapsedRouteId = remember(route.startedAtEpochMillis) {
        RouteTrackingService.routeId(route.startedAtEpochMillis)
    }
    val historicalSales = remember(previousRoutes, ledgerRevision) {
        previousRoutes.flatMap(CompletedRouteRecord::sales) + route.ledger.sales
    }
    val daysSinceLastSaleByStore = remember(historicalSales) { StoreMarkerPolicy.daysSinceLastSaleByStore(historicalSales) }

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
        // Aviso discreto en vez de abrir la lista encima del mapa al empezar.
        if (!startReminderShown && route.ledger.outstandingAccountStates().any(AccountSettlementPolicy::hasPending)) {
            startReminderShown = true
            pendingNoticeVisible = true
            delay(12_000)
            pendingNoticeVisible = false
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
                    daysSinceLastSale = daysSinceLastSaleByStore[store.id],
                )
            },
            moneyDue = { store -> route.ledger.storeAccount(store.id).totalOwed },
            unitsPending = { store ->
                route.ledger.products().sumOf { route.ledger.storeAccount(store.id).unitsPending(it.id) }
            },
            focusLocation = focusLocation,
            focusRequestId = focusRequestId,
            onLocationPermissionGranted = onLocationPermissionGranted,
            automaticFollow = settings.automaticMapFollow,
            gpsIntervalSeconds = settings.gpsIntervalSeconds,
            shareRouteGps = true,
            mapStyle = settings.mapStyle,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                    Text(route.name, fontWeight = FontWeight.Bold)
                    RouteClock(route.startedAtEpochMillis)
                }
            }
            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                TopNoticeVisibility(visible = pendingNoticeVisible) {
                    val pendingAccounts = remember(ledgerRevision) { route.ledger.outstandingAccountStates().filter(AccountSettlementPolicy::hasPending) }
                    PendingAccountsNotice(
                        count = pendingAccounts.size,
                        moneyDue = pendingAccounts.sumOf(AccountSettlementPolicy::moneyDue),
                        onOpen = {
                            pendingNoticeVisible = false
                            accountsOpen = true
                        },
                        onDismiss = { pendingNoticeVisible = false },
                    )
                }
            }
            Button(onClick = onExitMap) {
                Icon(Icons.Default.Menu, contentDescription = null)
                Text("Menú", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (!placingStore && landscape) {
            key(ledgerRevision) {
                RouteSummaryPanel(
                    route = route,
                    landscape = true,
                    expanded = panelExpanded,
                    onToggle = { panelExpanded = !panelExpanded },
                    onAccountsClick = { accountsOpen = true },
                    onAuditClick = { auditOpen = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 82.dp)
                        .width(if (panelExpanded) 270.dp else 68.dp),
                )
            }
        } else if (!placingStore) {
            key(ledgerRevision) {
                RouteSummaryPanel(
                    route = route,
                    landscape = false,
                    expanded = panelExpanded,
                    onToggle = { panelExpanded = !panelExpanded },
                    onAccountsClick = { accountsOpen = true },
                    onAuditClick = { auditOpen = true },
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 620.dp).fillMaxWidth()
                        .heightIn(max = if (panelExpanded) 460.dp else 72.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = actionMenuOpen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    bottom = if (landscape) 92.dp else if (panelExpanded) 282.dp else 126.dp,
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
                    bottom = if (landscape) 20.dp else if (panelExpanded) 210.dp else 58.dp,
                )
                .size(60.dp)
                .graphicsLayer { rotationZ = rotation },
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Acciones de ruta", modifier = Modifier.size(30.dp))
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
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RouteClock(startedAtEpochMillis: Long) {
    val context = LocalContext.current.applicationContext
    val routeId = remember(startedAtEpochMillis) { RouteTrackingService.routeId(startedAtEpochMillis) }
    var elapsedMillis by remember(startedAtEpochMillis) {
        mutableLongStateOf((System.currentTimeMillis() - startedAtEpochMillis).coerceAtLeast(0L))
    }
    // Aislado para que el tic de cada segundo no recomponga el mapa ni los marcadores.
    LaunchedEffect(startedAtEpochMillis) {
        RouteTrackingService.ensureElapsedClock(context, routeId)
        while (true) {
            elapsedMillis = RouteTrackingService.elapsedMillis(context, routeId)
            delay(1_000L)
        }
    }
    Text(formatRouteDuration(elapsedMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    landscape: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAccountsClick: () -> Unit,
    onAuditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    Card(
        modifier = modifier.padding(if (landscape) 8.dp else if (expanded) 12.dp else 4.dp)
            .animateContentSize().pointerInput(landscape, expanded) {
            detectDragGestures(
                onDragStart = { drag = Offset.Zero },
                onDragEnd = {
                    if (landscape) {
                        if (drag.x < -35f && !expanded) onToggle()
                        if (drag.x > 35f && expanded) onToggle()
                    } else {
                        if (drag.y < -35f && !expanded) onToggle()
                        if (drag.y > 35f && expanded) onToggle()
                    }
                },
                onDrag = { change, amount -> change.consume(); drag += amount },
            )
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (!expanded) {
            Box(
                // En vertical la tarjeta no tiene alto fijo: fillMaxSize la estiraba a toda la pantalla aun cerrada.
                modifier = (if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).clickable(onClick = onToggle).padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (landscape) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("‹", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Ruta", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Q${route.ledger.cashOnHand}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            route.ledger.products().filterNot { it.archived }
                                .joinToString("  ·  ") { "${it.name} ${route.ledger.displayStock(it.id)}" },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mostrar ruta e inventario", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.name, fontWeight = FontWeight.Bold)
                        RouteClock(route.startedAtEpochMillis)
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (landscape) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ocultar panel",
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Efectivo esperado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Q${route.ledger.cashOnHand}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("En el camión", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                route.ledger.products().filterNot { it.archived }.forEach { product ->
                    val quantity = route.ledger.displayStock(product.id)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(product.name)
                        Text(
                            quantity.toString(),
                            fontWeight = FontWeight.Bold,
                            color = if (quantity < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.size(2.dp))
                PanelActionButton(
                    icon = Icons.Default.Warning,
                    label = "Cuentas pendientes",
                    badge = route.ledger.outstandingAccountStates().count(AccountSettlementPolicy::hasPending).takeIf { it > 0 }?.toString(),
                    onClick = onAccountsClick,
                )
                PanelActionButton(
                    icon = Icons.Default.List,
                    label = "Movimientos",
                    badge = route.ledger.auditEntries.size.takeIf { it > 0 }?.toString(),
                    onClick = onAuditClick,
                )
            }
        }
    }
}

@Composable
private fun PanelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String?,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
        badge?.let {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// Fuera del Row: dentro, AnimatedVisibility toma la versión de RowScope y no compila.
@Composable
private fun TopNoticeVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) { content() }
}

@Composable
private fun PendingAccountsNotice(count: Int, moneyDue: Int, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 5.dp,
    ) {
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    if (count == 1) "1 tienda con pendientes" else "$count tiendas con pendientes",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (moneyDue > 0) "Q$moneyDue por cobrar · toca para ver" else "Toca para ver",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
