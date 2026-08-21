package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.SaleRecord
import com.example.caudalapp.domain.SuggestedPricePolicy

private data class QuickSaleLineUi(
    val product: Product,
    val quantity: Int,
    val total: Int,
    val returnMode: ReturnMode,
)

private enum class ReturnMode { RETURNED, SOLD }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSaleDialog(
    route: ActiveRoute,
    location: com.example.caudalapp.domain.GeoPoint? = null,
    knownCustomers: List<SaleRecord> = emptyList(),
    onDismiss: () -> Unit,
    onSaleRegistered: (units: Int, total: Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val products = remember(route) { route.ledger.products().filterNot(Product::archived) }
    var selectedProduct by remember { mutableStateOf(products.first()) }
    var quantityText by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var returnMode by remember { mutableStateOf(ReturnMode.RETURNED) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateListOf<QuickSaleLineUi>() }
    val cartTotal = cart.sumOf(QuickSaleLineUi::total)

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                Text("Venta rápida", fontWeight = FontWeight.Bold)
                Text("Solo pago al contado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Cliente (opcional)", fontWeight = FontWeight.SemiBold)
                    if (knownCustomers.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            knownCustomers.forEach { customer ->
                                FilterChip(
                                    selected = customerName == customer.customerName,
                                    onClick = {
                                        customerName = customer.customerName.orEmpty()
                                        customerPhone = customer.customerPhone.orEmpty()
                                    },
                                    label = { Text(customer.customerName.orEmpty()) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Nombre") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Teléfono") },
                            singleLine = true,
                        )
                    }
                }
                item {
                    Text("Producto", fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        products.forEach { product ->
                            FilterChip(
                                selected = selectedProduct.id == product.id,
                                onClick = {
                                    selectedProduct = product
                                    quantityText = ""
                                    totalText = ""
                                    returnMode = ReturnMode.RETURNED
                                },
                                label = { Text(product.name) },
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it.filter(Char::isDigit).take(5)
                                val quantity = quantityText.toIntOrNull() ?: 0
                                totalText = SuggestedPricePolicy.forQuantity(selectedProduct, quantity)?.toString().orEmpty()
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Cantidad") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = it.filter(Char::isDigit).take(7) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Total") },
                            prefix = { Text("Q") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true,
                        )
                    }
                }
                if (selectedProduct.returnable) {
                    item {
                        Text("Envase", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = returnMode == ReturnMode.RETURNED,
                                onClick = { returnMode = ReturnMode.RETURNED },
                                label = { Text("Devolvió vacío") },
                            )
                            FilterChip(
                                selected = returnMode == ReturnMode.SOLD,
                                onClick = { returnMode = ReturnMode.SOLD },
                                label = { Text("Compró envase") },
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            val quantity = quantityText.toIntOrNull() ?: 0
                            val total = totalText.toIntOrNull() ?: -1
                            if (quantity <= 0 || total < 0) {
                                error = "Escribe una cantidad y un total válidos"
                            } else {
                                cart += QuickSaleLineUi(selectedProduct, quantity, total, returnMode)
                                quantityText = ""
                                totalText = ""
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar producto") }
                }
                if (cart.isNotEmpty()) {
                    item { Text("Factura", fontWeight = FontWeight.Bold) }
                    itemsIndexed(cart) { index, line ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${line.quantity} × ${line.product.name}")
                            Row {
                                Text("Q${line.total}", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { cart.removeAt(index) }) { Text("Quitar") }
                            }
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", fontWeight = FontWeight.Bold)
                            Text("Q$cartTotal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = cart.isNotEmpty(),
                onClick = {
                    val lines = cart.map { line ->
                        SaleLine(
                            product = line.product,
                            requested = line.quantity,
                            delivered = line.quantity,
                            total = line.total,
                            emptyContainersReceived = if (line.product.returnable && line.returnMode == ReturnMode.RETURNED) line.quantity else 0,
                            containersSold = if (line.product.returnable && line.returnMode == ReturnMode.SOLD) line.quantity else 0,
                        )
                    }
                    val result = route.ledger.tryRegisterQuickSale(
                        lines,
                        amountReceived = cartTotal,
                        location = location,
                        customerName = customerName,
                        customerPhone = customerPhone,
                    )
                    if (result.accepted) {
                        onSaleRegistered(lines.sumOf(SaleLine::delivered), cartTotal)
                    } else {
                        error = result.reason
                    }
                },
            ) { Text("Cobrar Q$cartTotal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
