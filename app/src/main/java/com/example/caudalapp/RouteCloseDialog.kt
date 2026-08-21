package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.RouteCloseSummary

data class RouteCloseResult(
    val summary: RouteCloseSummary,
    val countedCash: Int? = null,
    val countedStock: Map<String, Int> = emptyMap(),
)

@Composable
fun RouteCloseDialog(
    route: ActiveRoute,
    onDismiss: () -> Unit,
    onFinish: (RouteCloseResult) -> Unit,
    onDiscard: () -> Unit,
) {
    val summary = route.ledger.closeSummary()
    var countPhysical by remember { mutableStateOf(false) }
    var cashText by remember { mutableStateOf("") }
    var discardConfirmation by remember { mutableStateOf(false) }
    val stock = remember {
        mutableStateMapOf<String, String>().apply {
            route.ledger.products().filterNot { it.archived }.forEach { put(it.id, "") }
        }
    }
    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text("¿Salir sin guardar?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se eliminarán ventas, gastos, recargas, transferencias, deudas nuevas y el recorrido GPS de esta ruta. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(onClick = onDiscard) { Text("Eliminar ruta") }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Finalizar ruta", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("Revisa el cierre antes de salir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                CloseRow("Sencillo inicial", "Q${summary.initialCash}")
                CloseRow("Ventas registradas", "Q${summary.totalSales}")
                CloseRow("Efectivo recibido", "Q${summary.cashCollected}")
                CloseRow("Fiado o cobro pendiente", "Q${summary.moneyPending}")
                CloseRow("Gastos", "−Q${summary.totalExpenses}")
                CloseRow("Efectivo esperado", "Q${summary.expectedCash}", emphasized = true)

                if (summary.hasWarnings) {
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pendientes que continuarán abiertos", color = Color(0xFFC45A00), fontWeight = FontWeight.Bold)
                        if (summary.pendingDeliveryUnits > 0) Text("• ${summary.pendingDeliveryUnits} productos por entregar")
                        if (summary.pendingContainers > 0) Text("• ${summary.pendingContainers} envases por recibir")
                        summary.negativeStock.forEach { (name, quantity) -> Text("• $name: $quantity") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Registrar conteo físico", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (countPhysical) "Dinero y producto entregados" else "Quedará marcado como omitido",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = countPhysical, onCheckedChange = { countPhysical = it })
                }
                if (countPhysical) {
                    OutlinedTextField(
                        value = cashText,
                        onValueChange = { cashText = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Efectivo contado") },
                        prefix = { Text("Q ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    route.ledger.products().filterNot { it.archived }.forEach { product ->
                        OutlinedTextField(
                            value = stock.getValue(product.id),
                            onValueChange = { stock[product.id] = it.filter(Char::isDigit).take(7) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("${product.name} físico") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onFinish(
                    RouteCloseResult(
                        summary = summary,
                        countedCash = cashText.toIntOrNull().takeIf { countPhysical },
                        countedStock = if (countPhysical) {
                            stock.mapValues { it.value.toIntOrNull() ?: 0 }
                        } else {
                            emptyMap()
                        },
                    ),
                )
            }) { Text("Finalizar ruta") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { discardConfirmation = true }) {
                    Text("Salir sin guardar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Seguir en ruta") }
            }
        },
    )
}

@Composable
private fun CloseRow(label: String, value: String, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
