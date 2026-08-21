package com.example.caudalapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.RouteTrackPoint
import com.example.caudalapp.domain.StoreDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteHistoryScreen(
    routes: List<CompletedRouteRecord>,
    stores: StoreDirectory,
    productName: (String) -> String,
    onDeleteRoute: (CompletedRouteRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRoute by remember { mutableStateOf<CompletedRouteRecord?>(null) }
    var deleteCandidate by remember { mutableStateOf<CompletedRouteRecord?>(null) }
    selectedRoute?.let { record ->
        RouteTrackDialog(record = record, stores = stores, onDismiss = { selectedRoute = null })
    }
    deleteCandidate?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("¿Eliminar esta ruta?", fontWeight = FontWeight.Bold) },
            text = { Text("Se borrarán el resumen y el recorrido GPS. Las tiendas y cuentas pendientes no cambiarán.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteRoute(record)
                    deleteCandidate = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } },
        )
    }
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Historial de rutas",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(72.dp))
        }
        if (routes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Todavía no hay rutas finalizadas", fontWeight = FontWeight.SemiBold)
                Text("El primer cierre aparecerá aquí", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(routes.asReversed(), key = { it.finishedAtEpochMillis }) { record ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) deleteCandidate = record
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                    ) {
                        HistoryRouteCard(record, productName = productName, onClick = { selectedRoute = record })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRouteCard(
    record: CompletedRouteRecord,
    productName: (String) -> String,
    onClick: () -> Unit,
) {
    val summary = record.summary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.routeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatHistoryDate(record.finishedAtEpochMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HistoryRow("Ventas", "Q${summary.totalSales}")
            HistoryRow("Efectivo esperado", "Q${summary.expectedCash}")
            HistoryRow("Fiado pendiente", "Q${summary.moneyPending}")
            HistoryRow("Gastos", "Q${summary.totalExpenses}")
            HistoryRow("Duración activa", formatHistoryDuration(record.activeDurationMillis))
            if (record.countedCash == null) {
                HistoryRow("Conteo físico", "Omitido")
            } else {
                HistoryRow("Efectivo contado", "Q${record.countedCash}")
                val difference = record.countedCash - summary.expectedCash
                HistoryRow(
                    if (difference >= 0) "Sobrante" else "Faltante",
                    "Q${kotlin.math.abs(difference)}",
                )
                (record.expectedStock.keys + record.countedStock.keys).forEach { productId ->
                    val expected = record.expectedStock[productId] ?: 0
                    val counted = record.countedStock[productId] ?: 0
                    val stockDifference = counted - expected
                    if (stockDifference != 0) {
                        HistoryRow(
                            "${productName(productId)} ${if (stockDifference > 0) "sobrante" else "faltante"}",
                            kotlin.math.abs(stockDifference).toString(),
                        )
                    }
                }
            }
            if (record.auditEntries.isNotEmpty()) {
                HistoryRow("Movimientos", record.auditEntries.size.toString())
            }
            if (record.trackPointCount > 0) {
                HistoryRow("Recorrido GPS", "${record.trackPointCount} puntos")
            }
            if (summary.hasWarnings) {
                Text(
                    "${summary.pendingDeliveryUnits} productos y ${summary.pendingContainers} envases pendientes",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RouteTrackDialog(record: CompletedRouteRecord, stores: StoreDirectory, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var points by remember(record.trackId) { mutableStateOf<List<RouteTrackPoint>>(emptyList()) }
    var loaded by remember(record.trackId) { mutableStateOf(false) }
    var auditOpen by remember { mutableStateOf(false) }
    val salePoints = remember(record.sales, stores) {
        record.sales.filterNot { it.cancelled }.mapNotNull { sale ->
            val location = sale.location ?: sale.storeId?.let { stores.get(it)?.location }
            location?.let { MapSalePoint(it, quickSale = sale.storeId == null) }
        }
    }
    if (auditOpen) {
        AuditListDialog(
            title = "Movimientos · ${record.routeName}",
            auditEntries = record.auditEntries,
            sales = record.sales,
            storeName = { id -> stores.get(id)?.name ?: "Tienda archivada" },
            onDismiss = { auditOpen = false },
        )
    }
    LaunchedEffect(record.trackId) {
        points = withContext(Dispatchers.IO) {
            record.trackId?.let { RouteTrackingService.trackStore(context).load(it) }.orEmpty()
                .downsampleForMap()
        }
        loaded = true
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("← Cerrar") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.routeName, fontWeight = FontWeight.Bold)
                        Text(
                            "Recorrido del ${formatHistoryDate(record.finishedAtEpochMillis)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { auditOpen = true }) { Text("Movimientos") }
                }
                when {
                    !loaded -> androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text("Cargando recorrido…") }
                    points.isEmpty() && salePoints.isEmpty() -> androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text("Esta ruta no tiene puntos GPS guardados") }
                    else -> RouteTrackMapView(
                        points = points,
                        sales = salePoints,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun List<RouteTrackPoint>.downsampleForMap(maxPoints: Int = 1_500): List<RouteTrackPoint> {
    if (size <= maxPoints) return this
    val step = kotlin.math.ceil(size.toDouble() / maxPoints).toInt()
    return filterIndexed { index, _ -> index == 0 || index == lastIndex || index % step == 0 }
}

@Composable
private fun HistoryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatHistoryDate(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(epochMillis))

private fun formatHistoryDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    return "%02d:%02d:%02d".format(
        totalSeconds / 3_600L,
        (totalSeconds % 3_600L) / 60L,
        totalSeconds % 60L,
    )
}
