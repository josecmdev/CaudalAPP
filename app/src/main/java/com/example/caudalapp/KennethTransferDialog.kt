package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.TransferDirection
import com.example.caudalapp.domain.TransferLine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KennethTransferDialog(
    route: ActiveRoute,
    onDismiss: () -> Unit,
    onTransferred: (TransferDirection, Int) -> Unit,
) {
    val products = remember(route) { route.ledger.products().filterNot(Product::archived) }
    var direction by remember { mutableStateOf(TransferDirection.TO_KENNETH) }
    var product by remember { mutableStateOf(products.first()) }
    var fullText by remember { mutableStateOf("") }
    var emptyText by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<TransferLine>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addLine() {
        val full = fullText.toIntOrNull() ?: 0
        val empty = emptyText.toIntOrNull() ?: 0
        val previous = lines.firstOrNull { it.product.id == product.id }
        val combinedFull = full + (previous?.fullUnits ?: 0)
        val combinedEmpty = empty + (previous?.emptyUnits ?: 0)
        when {
            full <= 0 && empty <= 0 -> error = "Escribe una cantidad"
            direction == TransferDirection.TO_KENNETH && combinedFull > route.ledger.availableStock(product.id) ->
                error = "Solo hay ${route.ledger.availableStock(product.id)} llenos"
            direction == TransferDirection.TO_KENNETH && combinedEmpty > route.ledger.emptyContainers(product.id) ->
                error = "Solo hay ${route.ledger.emptyContainers(product.id)} vacíos"
            else -> {
                lines = lines.filterNot { it.product.id == product.id } + TransferLine(
                    product = product,
                    fullUnits = combinedFull,
                    emptyUnits = combinedEmpty,
                )
                fullText = ""
                emptyText = ""
                error = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Transferencia con Kenneth", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == TransferDirection.TO_KENNETH,
                        onClick = { direction = TransferDirection.TO_KENNETH; lines = emptyList(); error = null },
                        label = { Text("Dar a Kenneth") },
                    )
                    FilterChip(
                        selected = direction == TransferDirection.FROM_KENNETH,
                        onClick = { direction = TransferDirection.FROM_KENNETH; lines = emptyList(); error = null },
                        label = { Text("Recibir de Kenneth") },
                    )
                }
                Text("Producto", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    products.forEach { item ->
                        FilterChip(
                            selected = product.id == item.id,
                            onClick = {
                                product = item
                                fullText = ""
                                emptyText = ""
                                error = null
                            },
                            label = { Text(item.name) },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuantityField(
                        value = fullText,
                        onValueChange = { fullText = it; error = null },
                        label = if (product.returnable) "Llenos" else "Cantidad",
                        modifier = Modifier.weight(1f),
                    )
                    if (product.returnable) {
                        QuantityField(
                            value = emptyText,
                            onValueChange = { emptyText = it; error = null },
                            label = "Vacíos",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TextButton(onClick = ::addLine, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar producto") }
                lines.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.product.name)
                        Text(
                            buildString {
                                if (line.fullUnits > 0) append("${line.fullUnits} llenos")
                                if (line.emptyUnits > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${line.emptyUnits} vacíos")
                                }
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (lines.isEmpty()) {
                    error = "Agrega al menos un producto"
                } else {
                    route.ledger.transferWithKenneth(direction, lines)
                    onTransferred(direction, lines.sumOf { it.fullUnits + it.emptyUnits })
                }
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QuantityField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
