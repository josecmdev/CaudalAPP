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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.caudalapp.sync.CaudalSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onExportDiagnostics: () -> Unit = {},
    routeActive: Boolean = false,
    onExportData: suspend () -> Unit = {},
    onRestoreData: suspend (Uri) -> Unit = {},
    onOpenProducts: () -> Unit = {},
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
            ChoiceRow(AppTheme.entries, settings.theme, { it.label }) { onSettingsChanged(settings.copy(theme = it)) }
            Text("Estilo del mapa", fontWeight = FontWeight.SemiBold)
            ChoiceRow(AppMapStyle.entries, settings.mapStyle, { it.label }) { onSettingsChanged(settings.copy(mapStyle = it)) }
        }
        SettingsCard("Productos y precios", "Agrega productos o cambia el precio sugerido de cada uno") {
            Button(onClick = onOpenProducts, modifier = Modifier.fillMaxWidth()) { Text("Editar productos y precios") }
        }
        SettingsCard("Seguimiento GPS", "Frecuencia de actualización durante una ruta") {
            ChoiceRow(listOf(1, 3, 5, 10), settings.gpsIntervalSeconds, { "${it} s" }) {
                onSettingsChanged(settings.copy(gpsIntervalSeconds = it))
            }
            Text(
                "1 s dibuja el recorrido con más detalle. 3 s gasta un poco menos de batería y basta para la ruta. " +
                    "Detenido, la app ya baja sola a una lectura cada 10 s.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        if (CaudalSync.configured) SyncSettingsCard()
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
                            onFailure = {
                                DiagnosticReporter.logError("Exportar respaldo", it)
                                "No se pudo crear el respaldo: ${it.message ?: "error desconocido"}"
                            },
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
                                onFailure = {
                                    DiagnosticReporter.logError("Restaurar respaldo", it)
                                    it.message ?: "No se pudo restaurar el respaldo."
                                },
                            )
                            backupBusy = false
                        }
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
            )
        }
        SettingsCard("Diagnóstico", "La app guarda sola los errores y cierres inesperados") {
            Text(
                "Si algo falla, toca el botón y manda el archivo .txt por WhatsApp, correo o Drive. " +
                    "Ese archivo es el que necesita Claude para encontrar y arreglar el problema.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExportDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Compartir reporte de errores (.txt)")
            }
        }
    }
}

@Composable
private fun SyncSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsCard("Cuenta y sincronización", "Sube ventas, rutas y ubicación a la oficina cuando hay internet") {
        val email = CaudalSync.signedInEmail
        if (email == null) {
            Text(
                "Sin cuenta la app funciona igual, solo que no envía datos. Pide tu usuario y contraseña al administrador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(user, { user = it }, label = { Text("Usuario o correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        error = withContext(Dispatchers.IO) { CaudalSync.signIn(context, user, password) }
                            .exceptionOrNull()?.let { "No se pudo entrar. Revisa usuario, contraseña e internet." }
                        if (error == null) password = ""
                        busy = false
                    }
                },
                enabled = !busy && user.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Entrar") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            Text("Conectado como $email", fontWeight = FontWeight.SemiBold)
            if (CaudalSync.status.isNotBlank()) Text(CaudalSync.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { CaudalSync.syncNow(context) }) { Text("Sincronizar ahora") }
                OutlinedButton(onClick = { CaudalSync.signOut(context) }) { Text("Cerrar sesión") }
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

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            androidx.compose.material3.FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option), modifier = Modifier.padding(vertical = 8.dp)) },
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}
