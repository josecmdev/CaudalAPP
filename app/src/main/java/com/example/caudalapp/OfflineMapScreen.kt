package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.StoreMarkerState

@Composable
fun OfflineMapScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { CaudalOfflineMaps(context) }
    var center by remember { mutableStateOf(GeoPoint(14.6349, -90.5069)) }
    var radiusKm by remember { mutableStateOf(15) }
    var status by remember { mutableStateOf(OfflineMapStatus()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { manager.refresh { status = it } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar mapas descargados?") },
            text = { Text("El mapa podrá descargarse nuevamente. Las rutas, tiendas y ventas no se borrarán.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    manager.deleteAll { success ->
                        status = if (success) OfflineMapStatus(message = "Mapas locales eliminados")
                        else status.copy(message = "No se pudieron eliminar todos los mapas")
                    }
                }) { Text("Borrar mapas") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        StoreMapLayer(
            stores = emptyList(),
            center = center,
            placementMode = true,
            showPlacementHint = false,
            onCenterChanged = { center = it },
            onStoreSelected = {},
            markerState = { StoreMarkerState.INACTIVE },
            moneyDue = { 0 },
            unitsPending = { 0 },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Volver") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mapa sin internet", fontWeight = FontWeight.Bold)
                    Text("Centra la mira en tu zona de trabajo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("Radio de descarga", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { radius ->
                        FilterChip(
                            selected = radiusKm == radius,
                            onClick = { radiusKm = radius },
                            label = { Text("$radius km") },
                        )
                    }
                }
                status.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (status.downloading) {
                    LinearProgressIndicator(
                        progress = { status.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${status.progressPercent}% · ${"%.1f".format(status.downloadedMegabytes)} MB")
                } else if (status.regionCount > 0) {
                    Text(
                        "${status.regionCount} zona(s) · ${"%.1f".format(status.downloadedMegabytes)} MB",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = { manager.download(center, radiusKm) { status = it } },
                    enabled = !status.downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Descargar esta zona") }
                if (status.regionCount > 0 && !status.downloading) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Borrar mapas descargados")
                    }
                }
            }
        }
    }
}
