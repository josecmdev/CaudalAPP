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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.SalePaymentPolicy
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreAccount
import com.example.caudalapp.domain.SuggestedPricePolicy

private data class StoreSaleLineUi(
    val product: Product,
    val requested: Int,
    val total: Int,
    val emptyContainers: Int,
    val containersSold: Int,
)

@Composable
fun StoreDetailsDialog(
    store: Store,
    account: StoreAccount,
    onDismiss: () -> Unit,
    onNewSale: () -> Unit,
    onCollectDebt: () -> Unit,
    onReceiveContainers: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onMove: () -> Unit,
) {
    val containersDue = account.pendingContainers.values.sum()
    val unitsPending = account.pendingDeliveries.values.sum()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(store.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AccountRow("Debe dinero", "Q${account.moneyDue}", account.moneyDue > 0)
                AccountRow("Debe envases", containersDue.toString(), containersDue > 0)
                AccountRow("Falta entregar", unitsPending.toString(), unitsPending > 0)
                Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth()) { Text("Nueva venta") }
                if (account.moneyDue > 0) {
                    Button(onClick = onCollectDebt, modifier = Modifier.fillMaxWidth()) { Text("Registrar pago") }
                }
                if (containersDue > 0) {
                    Button(onClick = onReceiveContainers, modifier = Modifier.fillMaxWidth()) { Text("Recibir envases") }
                }
                if (unitsPending > 0) {
                    Button(onClick = onCompleteDelivery, modifier = Modifier.fillMaxWidth()) { Text("Completar entrega") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onMove) { Text("Mover ubicación") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun AccountRow(label: String, value: String, alert: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreSaleDialog(
    route: ActiveRoute,
    store: Store,
    suggestedTotal: (Product, Int) -> Int? = SuggestedPricePolicy::forQuantity,
    onDismiss: () -> Unit,
    onRegistered: (units: Int, cash: Int, pending: Int) -> Unit,
) {
    val products = remember(route) { route.ledger.products().filterNot(Product::archived) }
    var selectedProduct by remember { mutableStateOf(products.first()) }
    var quantityText by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var emptiesText by remember { mutableStateOf("") }
    var soldContainersText by remember { mutableStateOf("") }
    var sellingContainers by remember { mutableStateOf(false) }
    var receivedText by remember { mutableStateOf("") }
    var creditEnabled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateListOf<StoreSaleLineUi>() }
    val invoiceTotal = cart.sumOf(StoreSaleLineUi::total)
    val hasPotentialPending = cart
        .groupBy { it.product.id }
        .any { (productId, lines) ->
            lines.sumOf(StoreSaleLineUi::requested) > route.ledger.availableStock(productId).coerceAtLeast(0)
        }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = RoundedCornerShape(26.dp),
        title = { Text("Venta · ${store.name}", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        products.forEach { product ->
                            FilterChip(
                                selected = selectedProduct.id == product.id,
                                onClick = {
                                    selectedProduct = product
                                    quantityText = ""
                                    totalText = ""
                                    emptiesText = ""
                                    soldContainersText = ""
                                    sellingContainers = false
                                },
                                label = { Text(product.name) },
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WholeNumberField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it
                                totalText = suggestedTotal(selectedProduct, it.toIntOrNull() ?: 0)
                                    ?.toString().orEmpty()
                                if (selectedProduct.returnable) emptiesText = it
                            },
                            label = "Cantidad",
                            modifier = Modifier.weight(1f),
                        )
                        WholeNumberField(
                            value = totalText,
                            onValueChange = { totalText = it },
                            label = "Total Q",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (selectedProduct.returnable) {
                    item {
                        WholeNumberField(
                            emptiesText,
                            { emptiesText = it },
                            "Vacíos recibidos",
                            Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("¿Compró el envase?", fontWeight = FontWeight.SemiBold)
                                Text("Actívalo solo si ya no debe devolverlo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = sellingContainers,
                                onCheckedChange = {
                                    sellingContainers = it
                                    soldContainersText = ""
                                },
                            )
                        }
                        if (sellingContainers) {
                            WholeNumberField(
                                soldContainersText,
                                { soldContainersText = it },
                                "Cantidad de envases comprados",
                                Modifier.fillMaxWidth(),
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
                                error = "Escribe cantidad y total"
                            } else {
                                cart += StoreSaleLineUi(
                                    selectedProduct,
                                    quantity,
                                    total,
                                    emptiesText.toIntOrNull() ?: 0,
                                    if (sellingContainers) soldContainersText.toIntOrNull() ?: 0 else 0,
                                )
                                quantityText = ""
                                totalText = ""
                                emptiesText = ""
                                soldContainersText = ""
                                sellingContainers = false
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar producto") }
                }
                itemsIndexed(cart) { index, line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.requested} × ${line.product.name}")
                        Row {
                            Text("Q${line.total}", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { cart.removeAt(index) }) { Text("Quitar") }
                        }
                    }
                }
                if (cart.isNotEmpty()) {
                    item {
                        Text("Total: Q$invoiceTotal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (hasPotentialPending) {
                            Text(
                                "Falta inventario: esta factura se cobrará al completar la entrega.",
                                modifier = Modifier.padding(top = 7.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("¿Quedó fiado?", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (creditEnabled) "Vacío significa Q0" else "Se cobrará Q$invoiceTotal",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = creditEnabled,
                                    onCheckedChange = {
                                        creditEnabled = it
                                        receivedText = ""
                                    },
                                )
                            }
                            if (creditEnabled) {
                                WholeNumberField(
                                    value = receivedText,
                                    onValueChange = { receivedText = it },
                                    label = "Efectivo recibido",
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) } }
            }
        },
        confirmButton = {
            Button(
                enabled = cart.isNotEmpty(),
                onClick = {
                    val remainingStock = products.associate { product ->
                        product.id to route.ledger.availableStock(product.id).coerceAtLeast(0)
                    }.toMutableMap()
                    val lines = cart.map { line ->
                        val delivered = minOf(line.requested, remainingStock[line.product.id] ?: 0)
                        remainingStock[line.product.id] = (remainingStock[line.product.id] ?: 0) - delivered
                        val emptyContainers = minOf(line.emptyContainers, delivered)
                        SaleLine(
                            product = line.product,
                            requested = line.requested,
                            delivered = delivered,
                            total = line.total,
                            emptyContainersReceived = emptyContainers,
                            containersSold = minOf(line.containersSold, delivered - emptyContainers),
                        )
                    }
                    val pending = lines.sumOf { it.requested - it.delivered }
                    val received = SalePaymentPolicy.amountReceived(
                        invoiceTotal = invoiceTotal,
                        creditEnabled = creditEnabled,
                        typedAmount = receivedText.toIntOrNull(),
                        hasPendingDelivery = pending > 0,
                    )
                    when {
                        received > invoiceTotal -> error = "El efectivo no puede superar Q$invoiceTotal"
                        else -> {
                            route.ledger.registerStoreSale(store.id, lines, received, location = store.location)
                            onRegistered(lines.sumOf(SaleLine::delivered), received, pending)
                        }
                    }
                },
            ) { Text("Registrar venta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun CollectDebtDialog(route: ActiveRoute, store: Store, onDismiss: () -> Unit, onCollected: (Int) -> Unit) {
    val due = route.ledger.storeAccount(store.id).moneyDue
    var amount by remember { mutableStateOf(due.toString()) }
    var error by remember { mutableStateOf(false) }
    SimpleAmountDialog(
        title = "Cobrar a ${store.name}",
        description = "Saldo pendiente: Q$due",
        value = amount,
        onValueChange = { amount = it; error = false },
        error = if (error) "El cobro debe estar entre Q1 y Q$due" else null,
        confirmLabel = "Registrar pago",
        onDismiss = onDismiss,
        onConfirm = {
            val value = amount.toIntOrNull() ?: 0
            if (value !in 1..due) error = true else {
                route.ledger.collectDebt(store.id, value)
                onCollected(value)
            }
        },
    )
}

@Composable
fun ReceiveContainersDialog(route: ActiveRoute, store: Store, onDismiss: () -> Unit, onReceived: (Int) -> Unit) {
    val products = route.ledger.products().filter { route.ledger.storeAccount(store.id).containersDue(it.id) > 0 }
    var product by remember { mutableStateOf(products.first()) }
    var quantity by remember { mutableStateOf(route.ledger.storeAccount(store.id).containersDue(product.id).toString()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Recibir envases") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    products.forEach { item ->
                        FilterChip(
                            selected = product.id == item.id,
                            onClick = {
                                product = item
                                quantity = route.ledger.storeAccount(store.id).containersDue(item.id).toString()
                            },
                            label = { Text(item.name) },
                        )
                    }
                }
                WholeNumberField(quantity, { quantity = it; error = false }, "Cantidad", Modifier.fillMaxWidth())
                if (error) Text("Escribe una cantidad mayor que cero", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = quantity.toIntOrNull() ?: 0
                if (value <= 0) error = true else {
                    route.ledger.receiveContainers(store.id, product.id, value)
                    onReceived(value)
                }
            }) { Text("Recibir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun CompleteDeliveryDialog(route: ActiveRoute, store: Store, onDismiss: () -> Unit, onCompleted: (Int, Int) -> Unit) {
    val products = route.ledger.products().filter { route.ledger.storeAccount(store.id).unitsPending(it.id) > 0 }
    var product by remember { mutableStateOf(products.first()) }
    var units by remember { mutableStateOf(route.ledger.storeAccount(store.id).unitsPending(product.id).toString()) }
    var suggestedAmount by remember {
        mutableStateOf(
            route.ledger.paymentDueAfterCompleting(
                store.id,
                product.id,
                route.ledger.storeAccount(store.id).unitsPending(product.id),
            ),
        )
    }
    var amount by remember { mutableStateOf(suggestedAmount.toString()) }
    var amountEdited by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Completar entrega") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    products.forEach { item ->
                        FilterChip(
                            selected = product.id == item.id,
                            onClick = {
                                product = item
                                units = route.ledger.storeAccount(store.id).unitsPending(item.id).toString()
                                suggestedAmount = route.ledger.paymentDueAfterCompleting(
                                    store.id,
                                    item.id,
                                    route.ledger.storeAccount(store.id).unitsPending(item.id),
                                )
                                amount = suggestedAmount.toString()
                                amountEdited = false
                            },
                            label = { Text(item.name) },
                        )
                    }
                }
                WholeNumberField(
                    units,
                    {
                        units = it
                        suggestedAmount = route.ledger.paymentDueAfterCompleting(
                            store.id,
                            product.id,
                            it.toIntOrNull() ?: 0,
                        )
                        if (!amountEdited) amount = suggestedAmount.toString()
                    },
                    "Cantidad entregada",
                    Modifier.fillMaxWidth(),
                )
                WholeNumberField(
                    amount,
                    {
                        amount = it
                        amountEdited = true
                    },
                    "Efectivo recibido (sugerido Q$suggestedAmount)",
                    Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused && !amountEdited && amount == suggestedAmount.toString()) {
                            amount = ""
                        }
                    },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val quantity = units.toIntOrNull() ?: 0
                val cash = amount.toIntOrNull() ?: 0
                val pending = route.ledger.storeAccount(store.id).unitsPending(product.id)
                val paymentDue = route.ledger.paymentDueAfterCompleting(store.id, product.id, quantity)
                if (quantity !in 1..pending) {
                    error = "La cantidad máxima pendiente es $pending"
                } else if (cash > 0 && paymentDue == 0) {
                    error = "El cobro se registra al completar toda la factura"
                } else if (cash > paymentDue) {
                    error = "El efectivo no puede superar Q$paymentDue"
                } else {
                    route.ledger.completeDelivery(store.id, product.id, quantity, cash)
                    onCompleted(quantity, cash)
                }
            }) { Text("Completar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SimpleAmountDialog(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column {
                Text(description)
                WholeNumberField(value, onValueChange, "Quetzales", Modifier.fillMaxWidth().padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WholeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(7)) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        ),
        singleLine = true,
    )
}
