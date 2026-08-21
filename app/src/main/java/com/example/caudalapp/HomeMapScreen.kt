package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
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
    settings: AppSettings = AppSettings(),
    modifier: Modifier = Modifier,
) {
    var revision by remember { mutableIntStateOf(0) }
    var center by remember { mutableStateOf(GeoPoint(14.6349, -90.5069)) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var placementMode by remember { mutableStateOf(false) }
    var namingLocation by remember { mutableStateOf<GeoPoint?>(null) }
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
        val moneyDue = account.moneyDue()
        val unitsPending = account.unitsPending()
        val containersDue = account?.pendingContainers?.values?.sum() ?: 0
        AlertDialog(
            onDismissRequest = { selectedStore = null },
            title = { Text(store.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    store.phone?.let { Text("Teléfono: $it") }
                    Text("${"%.5f".format(store.location.latitude)}, ${"%.5f".format(store.location.longitude)}")
                    if (moneyDue > 0 || unitsPending > 0 || containersDue > 0) {
                        Text(
                            "Debe Q$moneyDue · $unitsPending productos · $containersDue envases",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text("Sin cuentas pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStore = null }) { Text("Cerrar") }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
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
                    else -> {
                        val days = StoreMarkerPolicy.daysSinceLastSale(store.id, historicalSales)
                        when {
                            days != null && days <= 3 -> StoreMarkerState.RECENT
                            days != null && days <= 7 -> StoreMarkerState.DUE_SOON
                            else -> StoreMarkerState.INACTIVE
                        }
                    }
                }
            },
            moneyDue = { store -> accountByStore[store.id].moneyDue() },
            unitsPending = { store -> accountByStore[store.id].unitsPending() },
            automaticFollow = settings.automaticMapFollow,
            gpsIntervalSeconds = settings.gpsIntervalSeconds,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Mapa de tiendas", fontWeight = FontWeight.Bold)
                    Text(
                        "${displayedStores.size} tiendas",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onExitMap) { Text("Salir del mapa") }
            }
        }

        if (placementMode) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { placementMode = false }) {
                    Text("Cancelar")
                }
                Button(onClick = {
                    placementMode = false
                    namingLocation = center
                }) {
                    Text("Usar este punto")
                }
            }
        } else {
            Button(
                onClick = { placementMode = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).size(58.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("+", fontSize = 28.sp)
            }
            Button(
                onClick = onStartRoute,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    "Iniciar ruta",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun StoreAccountState?.moneyDue(): Int = this?.let { account ->
    account.debts.sumOf { it.remainingAmount } + account.pendingPayments.values.sum()
} ?: 0

private fun StoreAccountState?.unitsPending(): Int = this?.pendingDeliveries?.values?.sum() ?: 0
