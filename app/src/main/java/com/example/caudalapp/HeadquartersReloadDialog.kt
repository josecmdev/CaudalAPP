package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product

@Composable
fun HeadquartersReloadDialog(
    route: ActiveRoute,
    onDismiss: () -> Unit,
    onReloaded: (loaded: Int, unloaded: Int) -> Unit,
) {
    val products = remember(route) { route.ledger.products().filterNot(Product::archived) }
    val loads = remember { mutableStateMapOf<String, String>().apply { products.forEach { put(it.id, "") } } }
    val unloaded = remember {
        mutableStateMapOf<String, String>().apply {
            products.filter(Product::returnable).forEach { put(it.id, "") }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = RoundedCornerShape(26.dp),
        title = { Text("Recargar en sede", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Producto nuevo que sube al camión", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                products.forEach { product ->
                    item(key = "load-${product.id}") {
                        OutlinedTextField(
                            value = loads.getValue(product.id),
                            onValueChange = { loads[product.id] = it.onlyDigits() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(product.name) },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
                item {
                    Text("Vacíos que se bajan del camión", fontWeight = FontWeight.SemiBold)
                }
                products.filter(Product::returnable).forEach { product ->
                    item(key = "unload-${product.id}") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = unloaded.getValue(product.id),
                                onValueChange = { unloaded[product.id] = it.onlyDigits() },
                                modifier = Modifier.weight(1f),
                                label = { Text("${product.name} vacíos") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            Text(
                                "Hay ${route.ledger.emptyContainers(product.id)}",
                                modifier = Modifier.weight(.55f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val loadValues = loads.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }
                val unloadValues = unloaded.mapValues { it.value.toIntOrNull() ?: 0 }.filterValues { it > 0 }
                val invalidUnload = unloadValues.entries.firstOrNull {
                    it.value > route.ledger.emptyContainers(it.key)
                }
                when {
                    loadValues.isEmpty() && unloadValues.isEmpty() -> error = "Escribe al menos una cantidad"
                    invalidUnload != null -> error = "No hay tantos envases vacíos para descargar"
                    else -> {
                        route.ledger.restock(
                            loadValues.mapKeys { (productId, _) -> products.first { it.id == productId } },
                        )
                        unloadValues.forEach { (productId, quantity) ->
                            route.ledger.unloadEmptyContainers(productId, quantity)
                        }
                        onReloaded(loadValues.values.sum(), unloadValues.values.sum())
                    }
                }
            }) { Text("Registrar recarga") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun String.onlyDigits(): String = filter(Char::isDigit).take(7)
