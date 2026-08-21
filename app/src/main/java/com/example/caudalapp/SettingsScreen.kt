package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onExportDiagnostics: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        SettingsCard("Datos y escalabilidad", "Versión local preparada para respaldo y sincronización futura") {
            Text("Almacenamiento: local en esta tablet")
            Text("Caudal App 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
