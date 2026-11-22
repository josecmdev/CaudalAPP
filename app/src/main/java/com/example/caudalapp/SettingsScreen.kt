package com.example.caudalapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onExportDiagnostics: () -> Unit = {},
    routeActive: Boolean = false,
    onExportData: suspend () -> Unit = {},
    onRestoreData: suspend (Uri) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        SettingsCard("Apariencia", "Elige cómo se muestra Caudal App") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    OutlinedButton(onClick = { onSettingsChanged(settings.copy(theme = theme)) }) {
                        Text(if (settings.theme == theme) "✓ ${theme.label}" else theme.label)
                    }
                }
            }
            Text("Estilo del mapa", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppMapStyle.entries.forEach { mapStyle ->
                    OutlinedButton(onClick = { onSettingsChanged(settings.copy(mapStyle = mapStyle)) }) {
                        Text(if (settings.mapStyle == mapStyle) "✓ ${mapStyle.label}" else mapStyle.label)
                    }
                }
            }
        }
        SettingsCard("Seguimiento GPS", "Frecuencia de actualización durante una ruta") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { seconds ->
                    OutlinedButton(onClick = {
                        onSettingsChanged(settings.copy(gpsIntervalSeconds = seconds))
                    }) {
                        Text(if (settings.gpsIntervalSeconds == seconds) "✓ ${seconds}s" else "${seconds}s")
                    }
                }
            }
            SettingsSwitch(
                title = "Seguimiento automático",
                description = "El mapa vuelve al vehículo después de moverlo",
                checked = settings.automaticMapFollow,
                onCheckedChange = { onSettingsChanged(settings.copy(automaticMapFollow = it)) },
            )
        }
        SettingsCard("Interacción", "Respuesta al confirmar operaciones") {
            SettingsSwitch(
                title = "Vibración",
                description = "Confirma ventas y cambios con respuesta táctil",
                checked = settings.hapticFeedback,
                onCheckedChange = { onSettingsChanged(settings.copy(hapticFeedback = it)) },
            )
        }
        SettingsCard("Marcadores personalizados", "Los íconos se cargan desde assets/map/markers") {
            Text(
                "Puedes reemplazar los cinco PNG de estado sin cambiar el código. Si falta alguno, se usa el marcador de Caudal App.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsCard("Respaldo de datos", "Para no perder la información si se pierde o se cambia la tablet") {
            Text(
                "Exporta un archivo con rutas, ventas, cuentas, tiendas, productos y recorridos GPS. " +
                    "Guárdalo en Google Drive, correo o WhatsApp. Con ese archivo puedes restaurar todo en otra tablet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Además, Android guarda una copia automática en la cuenta de Google de la tablet si la copia de seguridad está activada.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    backupBusy = true
                    backupMessage = null
                    scope.launch {
                        backupMessage = runCatching { onExportData() }.fold(
                            onSuccess = { null },
                            onFailure = { "No se pudo crear el respaldo: ${it.message ?: "error desconocido"}" },
                        )
                        backupBusy = false
                    }
                },
                enabled = !backupBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exportar respaldo")
            }
            OutlinedButton(
                onClick = {
                    backupMessage = null
                    restorePicker.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
                },
                enabled = !backupBusy && !routeActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restaurar desde respaldo")
            }
            if (routeActive) {
                Text(
                    "Cierra la ruta activa para poder restaurar un respaldo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            backupMessage?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            Text("Caudal App 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        pendingRestore?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("¿Restaurar respaldo?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Los datos actuales de esta tablet se reemplazarán por los del respaldo. " +
                            "Antes se guarda una copia de los datos actuales dentro de la app.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        pendingRestore = null
                        backupBusy = true
                        scope.launch {
                            backupMessage = runCatching { onRestoreData(uri) }.fold(
                                onSuccess = { "Respaldo restaurado correctamente." },
                                onFailure = { it.message ?: "No se pudo restaurar el respaldo." },
                            )
                            backupBusy = false
                        }
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
            )
        }
        SettingsCard("Diagnóstico", "Guarda los errores aunque no lleves la computadora") {
            Text(
                "Genera un archivo que puedes enviar a Google Drive, correo, WhatsApp u otra aplicación instalada.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Enviar reporte de diagnóstico")
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
