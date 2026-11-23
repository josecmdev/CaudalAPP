package com.example.caudalapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.QuantityInput
import com.example.caudalapp.domain.RouteDraft
import com.example.caudalapp.domain.RoutePreparation
import com.example.caudalapp.domain.RouteStartResult

private val RouteDays = listOf("Martes", "Jueves", "Viernes")
private val QuickCash = listOf(100, 150, 200, 300)
private const val DEFAULT_INITIAL_CASH = "150"

@Composable
fun RoutePreparationScreen(
    products: List<Product>,
    onBack: () -> Unit,
    onRouteStarted: (ActiveRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var routeName by rememberSaveable { mutableStateOf("") }
    var initialCash by rememberSaveable { mutableStateOf(DEFAULT_INITIAL_CASH) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val stock = remember(products) { mutableStateMapOf<String, String>().apply { products.forEach { put(it.id, "") } } }
    val focusManager = LocalFocusManager.current

    val startRoute = {
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
            is RouteStartResult.Rejected -> {
                errorMessage = result.errors.joinToString("\n") { it.message }
                starting = false
            }
        }
    }
    // Crear el mapa de la ruta bloquea un momento: primero se pinta "Iniciando…" para que el toque se sienta al instante.
    LaunchedEffect(starting) {
        if (!starting) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        startRoute()
    }

    // Scaffold padding already covers the nav bar; without consuming it imePadding counts it twice.
    BoxWithConstraints(modifier = modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars).imePadding()) {
        val wide = maxWidth >= 760.dp
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RoundButton("←", "Volver", onClick = onBack)
                Column {
                    Text("Preparar ruta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Sencillo e inventario del camión", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val details: @Composable ColumnScope.() -> Unit = {
                RouteDetailsCard(
                    routeName = routeName,
                    onRouteNameChange = { routeName = it; errorMessage = null },
                    initialCash = initialCash,
                    onInitialCashChange = { initialCash = QuantityInput.sanitize(it); errorMessage = null },
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                )
            }
            val load: @Composable ColumnScope.() -> Unit = {
                TruckLoadCard(
                    products = products,
                    quantityOf = { stock.getValue(it.id) },
                    onQuantityChange = { product, value -> stock[product.id] = value; errorMessage = null },
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { focusManager.clearFocus() },
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                        content = details,
                    )
                    Column(
                        Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                        content = load,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    details()
                    load()
                }
            }

            StartRouteBar(
                summary = summaryText(products.count { (stock[it.id]?.toIntOrNull() ?: 0) > 0 }, products.size, initialCash),
                errorMessage = errorMessage,
                starting = starting,
                onStart = { starting = true },
            )
        }
    }
}

@Composable
private fun RouteDetailsCard(
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    initialCash: String,
    onInitialCashChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    PrepCard(title = "Datos de la ruta") {
        SectionLabel("Día o nombre")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteDays.forEach { day ->
                FilterChip(
                    selected = routeName == day,
                    onClick = { onRouteNameChange(day) },
                    label = { Text(day, modifier = Modifier.padding(vertical = 8.dp)) },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
        OutlinedTextField(
            value = routeName,
            onValueChange = onRouteNameChange,
            modifier = Modifier.fillMaxWidth().testTag("routeName"),
            placeholder = { Text("Otro nombre") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            shape = RoundedCornerShape(16.dp),
        )

        SectionLabel("Sencillo inicial", Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = initialCash,
            onValueChange = onInitialCashChange,
            modifier = Modifier.fillMaxWidth().testTag("initialCash"),
            prefix = { Text("Q ") },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(16.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickCash.forEach { amount ->
                FilterChip(
                    selected = initialCash == amount.toString(),
                    onClick = { onInitialCashChange(amount.toString()) },
                    label = { Text("Q$amount", modifier = Modifier.padding(vertical = 8.dp)) },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TruckLoadCard(
    products: List<Product>,
    quantityOf: (Product) -> String,
    onQuantityChange: (Product, String) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    PrepCard(title = "Carga del camión", subtitle = "Lo que se cargó hoy. Usa + y − o escribe la cantidad.") {
        if (products.isEmpty()) {
            Text("No hay productos activos. Agrégalos en Productos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        products.forEachIndexed { index, product ->
            val quantity = quantityOf(product)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProductImage(product, Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    if (product.returnable) {
                        Text("Lleva envase", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                RoundButton("−", "Restar ${product.name}", tonal = true) { onQuantityChange(product, QuantityInput.step(quantity, -1)) }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { onQuantityChange(product, QuantityInput.sanitize(it)) },
                    modifier = Modifier.width(96.dp).testTag("qty_${product.id}"),
                    placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == products.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                RoundButton("+", "Sumar ${product.name}", tonal = true) { onQuantityChange(product, QuantityInput.step(quantity, 1)) }
            }
        }
    }
}

@Composable
private fun StartRouteBar(summary: String, errorMessage: String?, starting: Boolean, onStart: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 10.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("routeError"))
                }
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onStart,
                enabled = !starting,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            ) { Text(if (starting) "Iniciando…" else "Iniciar ruta", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun PrepCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RoundButton(symbol: String, description: String, tonal: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.size(52.dp).semantics { contentDescription = description }
    val padding = PaddingValues(0.dp)
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = CircleShape, contentPadding = padding) {
            Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(onClick = onClick, modifier = modifier, shape = CircleShape, contentPadding = padding) {
            Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun summaryText(loaded: Int, total: Int, cash: String): String =
    "$loaded de $total productos cargados · Q${cash.ifEmpty { "0" }} de sencillo"
