package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.RouteDraft
import com.example.caudalapp.domain.RoutePreparation
import com.example.caudalapp.domain.RouteStartResult

@Composable
fun RoutePreparationScreen(
    products: List<Product>,
    onBack: () -> Unit,
    onRouteStarted: (ActiveRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var routeName by rememberSaveable { mutableStateOf("") }
    var initialCash by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val stock = remember(products) { mutableStateMapOf<String, String>().apply { products.forEach { put(it.id, "") } } }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Preparar ruta",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp).weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Nombre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Martes", "Jueves", "Viernes").forEach { day ->
                        FilterChip(
                            selected = routeName == day,
                            onClick = { routeName = day },
                            label = { Text(day) },
                        )
                    }
                }
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Nombre libre (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                Text("Sencillo inicial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = initialCash,
                    onValueChange = { initialCash = it.onlyWholeNumber() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Quetzales") },
                    prefix = { Text("Q ") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                Text("Inventario inicial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Escribe lo que se cargó hoy en el camión.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            itemsIndexed(products, key = { _, product -> product.id }) { index, product ->
                InventoryInputRow(
                    product = product,
                    quantity = stock.getValue(product.id),
                    onQuantityChange = { stock[product.id] = it.onlyWholeNumber() },
                    isLast = index == products.lastIndex,
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { focusManager.clearFocus() },
                )
            }
            errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = {
                    val result = RoutePreparation.start(
                        draft = RouteDraft(
                            name = routeName,
                            initialCash = initialCash.toIntOrNull() ?: 0,
                            stock = stock.mapValues { it.value.toIntOrNull() ?: 0 },
                            emptyContainers = emptyMap(),
                        ),
                        activeRouteExists = false,
                        availableProducts = products,
                    )
                    when (result) {
                        is RouteStartResult.Started -> onRouteStarted(result.route)
                        is RouteStartResult.Rejected -> errorMessage = result.errors.joinToString("\n") { it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(16.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Iniciar ruta", modifier = Modifier.padding(vertical = 7.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InventoryInputRow(
    product: Product,
    quantity: String,
    onQuantityChange: (String) -> Unit,
    isLast: Boolean,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = quantity,
                onValueChange = onQuantityChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Cantidad") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onNext() },
                    onDone = { onDone() },
                ),
                singleLine = true,
            )
        }
    }
}

private fun String.onlyWholeNumber(): String = filter(Char::isDigit).take(7)
