package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.StoreMarkerState
import com.example.caudalapp.domain.StoreObligations

@Composable
fun StoresScreen(
    stores: StoreDirectory,
    accounts: List<StoreAccountState>,
    initialFocus: GeoPoint? = null,
    onBack: () -> Unit,
    onStateChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revision by remember { mutableIntStateOf(0) }
    var center by remember { mutableStateOf(GeoPoint(14.6349, -90.5069)) }
    var placementMode by remember { mutableStateOf(false) }
    var movingStore by remember { mutableStateOf<Store?>(null) }
    var namingLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var editingStore by remember { mutableStateOf<Store?>(null) }
    var archiveCandidate by remember { mutableStateOf<Store?>(null) }
    var archivedOpen by remember { mutableStateOf(false) }

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
        StoreAdminDialog(
            store = store,
            obligations = obligationsFor(store.id, accounts),
            onDismiss = { selectedStore = null },
            onEdit = { selectedStore = null; editingStore = store },
            onMove = {
                selectedStore = null
                movingStore = store
                center = store.location
                placementMode = true
            },
            onArchive = { selectedStore = null; archiveCandidate = store },
        )
    }
    editingStore?.let { store ->
        EditStoreDialog(
            store = store,
            onDismiss = { editingStore = null },
            onSave = { name, phone ->
                if (stores.updateStore(store.id, name, phone) is StoreChangeResult.Success) {
                    editingStore = null
                    revision++
                    onStateChanged()
                }
            },
        )
    }
    archiveCandidate?.let { store ->
        val obligations = obligationsFor(store.id, accounts)
        ArchiveStoreDialog(
            store = store,
            obligations = obligations,
            onDismiss = { archiveCandidate = null },
            onConfirm = {
                stores.archiveStore(store.id, obligations, confirmed = true)
                archiveCandidate = null
                revision++
                onStateChanged()
            },
        )
    }
    if (archivedOpen) {
        ArchivedStoresDialog(
            stores = stores.allStores().filter(Store::archived),
            onDismiss = { archivedOpen = false },
            onRestore = { store ->
                stores.restoreStore(store.id)
                revision++
                onStateChanged()
            },
        )
    }

    val displayedStores = remember(revision) { stores.activeStores() }
    Box(modifier = modifier.fillMaxSize()) {
        StoreMapLayer(
            stores = displayedStores,
            center = center,
            placementMode = placementMode,
            onCenterChanged = { center = it },
            onStoreSelected = { selectedStore = it },
            markerState = { StoreMarkerState.INACTIVE },
            moneyDue = { store -> obligationsFor(store.id, accounts).moneyDue },
            unitsPending = { store -> obligationsFor(store.id, accounts).unitsPending },
            focusLocation = initialFocus,
            focusRequestId = if (initialFocus == null) 0 else 1,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Volver") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tiendas", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(
                        "${displayedStores.size} activas",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { archivedOpen = true }) {
                    Text("Archivadas (${stores.allStores().count(Store::archived)})")
                }
            }
        }

        if (placementMode) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { placementMode = false; movingStore = null }) { Text("Cancelar") }
                Button(onClick = {
                    val moving = movingStore
                    if (moving == null) {
                        placementMode = false
                        namingLocation = center
                    } else if (stores.moveStore(moving.id, center) is StoreChangeResult.Success) {
                        placementMode = false
                        movingStore = null
                        revision++
                        onStateChanged()
                    }
                }) {
                    Text(if (movingStore == null) "Usar este punto" else "Guardar posición")
                }
            }
        } else {
            Button(
                onClick = { placementMode = true; movingStore = null },
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).size(58.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("+", fontSize = 28.sp) }
        }
    }
}

private fun obligationsFor(storeId: String, accounts: List<StoreAccountState>): StoreObligations {
    val account = accounts.firstOrNull { it.storeId == storeId } ?: return StoreObligations()
    return StoreObligations(
        moneyDue = account.debts.sumOf { it.remainingAmount } + account.pendingPayments.values.sum(),
        containersDue = account.pendingContainers.values.sum(),
        unitsPending = account.pendingDeliveries.values.sum(),
    )
}

@Composable
private fun StoreAdminDialog(
    store: Store,
    obligations: StoreObligations,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onArchive: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(store.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                store.phone?.let { Text("Teléfono: $it") }
                Text("${"%.5f".format(store.location.latitude)}, ${"%.5f".format(store.location.longitude)}")
                if (obligations.hasAny) {
                    Text(
                        "Debe Q${obligations.moneyDue} · ${obligations.containersDue} envases · ${obligations.unitsPending} productos pendientes",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar datos") }
                OutlinedButton(onClick = onMove, modifier = Modifier.fillMaxWidth()) { Text("Mover en el mapa") }
                TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("Archivar tienda") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun EditStoreDialog(store: Store, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember(store.id) { mutableStateOf(store.name) }
    var phone by remember(store.id) { mutableStateOf(store.phone.orEmpty()) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Editar tienda", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono (opcional)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name, phone) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ArchiveStoreDialog(
    store: Store,
    obligations: StoreObligations,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archivar ${store.name}?") },
        text = {
            Text(
                if (obligations.hasAny) {
                    "La tienda tiene deuda, envases o entregas pendientes. Se ocultará del mapa, pero sus cuentas y ventas seguirán guardadas."
                } else {
                    "Se ocultará del mapa. Podrás restaurarla después."
                },
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Archivar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ArchivedStoresDialog(stores: List<Store>, onDismiss: () -> Unit, onRestore: (Store) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiendas archivadas", fontWeight = FontWeight.Bold) },
        text = {
            if (stores.isEmpty()) {
                Text("No hay tiendas archivadas")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stores, key = Store::id) { store ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(store.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRestore(store) }) { Text("Restaurar") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
