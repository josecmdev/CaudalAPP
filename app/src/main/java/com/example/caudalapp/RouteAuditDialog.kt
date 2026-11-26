package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.RouteAuditEntry
import com.example.caudalapp.domain.RouteAuditType
import com.example.caudalapp.domain.SaleRecord
import com.example.caudalapp.domain.RouteExpense
import com.example.caudalapp.domain.StoreDirectory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveRouteAuditDialog(
    route: ActiveRoute,
    stores: StoreDirectory,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var cancellationSale by remember { mutableStateOf<SaleRecord?>(null) }
    var cancellationExpense by remember { mutableStateOf<RouteExpense?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    cancellationSale?.let { sale ->
        CancelSaleDialog(
            sale = sale,
            onDismiss = { cancellationSale = null },
            onConfirm = { reason ->
                val result = route.ledger.cancelSale(sale.id, reason)
                if (result.accepted) {
                    cancellationSale = null
                    onChanged("Venta anulada y registrada")
                } else {
                    error = result.reason
                    cancellationSale = null
                }
            },
        )
    }
    cancellationExpense?.let { expense ->
        CancelExpenseDialog(
            expense = expense,
            onDismiss = { cancellationExpense = null },
            onConfirm = { reason ->
                if (route.ledger.cancelExpense(expense.id, reason)) {
                    cancellationExpense = null
                    onChanged("Gasto anulado y registrado")
                } else {
                    error = "El gasto ya no puede anularse"
                    cancellationExpense = null
                }
            },
        )
    }
    AuditListDialog(
        title = "Movimientos de la ruta",
        auditEntries = route.ledger.auditEntries,
        sales = route.ledger.sales,
        expenses = route.ledger.expenses,
        storeName = { id -> stores.get(id)?.name ?: "Tienda archivada" },
        error = error,
        onDismiss = onDismiss,
        onCancelSale = { cancellationSale = it },
        onCancelExpense = { cancellationExpense = it },
    )
}

@Composable
fun AuditListDialog(
    title: String,
    auditEntries: List<RouteAuditEntry>,
    sales: List<SaleRecord>,
    expenses: List<RouteExpense> = emptyList(),
    storeName: (String) -> String,
    error: String? = null,
    onDismiss: () -> Unit,
    onCancelSale: ((SaleRecord) -> Unit)? = null,
    onCancelExpense: ((RouteExpense) -> Unit)? = null,
) {
    val salesById = sales.associateBy(SaleRecord::id)
    val expensesById = expenses.associateBy(RouteExpense::id)
    SidePanelDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                if (auditEntries.isEmpty()) {
                    Text("Todavía no hay movimientos", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Aquí aparecen ventas, cobros, recargas, gastos y transferencias en orden, con la hora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val cashIn = auditEntries.sumOf { it.cashDelta.coerceAtLeast(0) }
                    val cashOut = auditEntries.sumOf { (-it.cashDelta).coerceAtLeast(0) }
                    Text(
                        "${auditEntries.size} movimientos · +Q$cashIn entrada · −Q$cashOut salida",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(auditEntries.asReversed(), key = RouteAuditEntry::id) { entry ->
                            val sale = entry.relatedSaleId?.let(salesById::get)
                            val expense = entry.relatedExpenseId?.let(expensesById::get)
                            AuditEntryCard(
                                entry = entry,
                                sale = sale,
                                expense = expense,
                                storeName = sale?.storeId?.let(storeName),
                                onCancel = onCancelSale?.takeIf {
                                    sale != null && !sale.cancelled &&
                                        entry.type in setOf(RouteAuditType.STORE_SALE, RouteAuditType.QUICK_SALE)
                                }?.let { callback -> ({ callback(sale!!) }) },
                                onCancelExpense = onCancelExpense?.takeIf {
                                    expense != null && !expense.cancelled && entry.type == RouteAuditType.EXPENSE
                                }?.let { callback -> ({ callback(expense!!) }) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = {},
    )
}

@Composable
private fun AuditEntryCard(
    entry: RouteAuditEntry,
    sale: SaleRecord?,
    expense: RouteExpense?,
    storeName: String?,
    onCancel: (() -> Unit)?,
    onCancelExpense: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatAuditTime(entry.recordedAtEpochMillis),
                    modifier = Modifier.padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, fontWeight = FontWeight.Bold)
                    storeName?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    Text(entry.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.cashDelta != 0) {
                    Text(
                        if (entry.cashDelta > 0) "+Q${entry.cashDelta}" else "−Q${-entry.cashDelta}",
                        color = if (entry.cashDelta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (sale?.cancelled == true) {
                Text(
                    "ANULADA: ${sale.cancellationReason}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            } else if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Anular venta") }
            }
            if (expense?.cancelled == true) {
                Text(
                    "ANULADO: ${expense.cancellationReason}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            } else if (onCancelExpense != null) {
                TextButton(onClick = onCancelExpense) { Text("Anular o corregir gasto") }
            }
        }
    }
}

@Composable
private fun CancelSaleDialog(sale: SaleRecord, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember(sale.id) { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Anular venta de Q${sale.total}?") },
        text = {
            Column {
                Text("La venta seguirá visible en el historial y los cálculos se revertirán.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; showError = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("Motivo obligatorio") },
                    isError = showError,
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (reason.isBlank()) showError = true else onConfirm(reason) }) { Text("Anular") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CancelExpenseDialog(
    expense: RouteExpense,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember(expense.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Anular gasto de Q${expense.amount}?") },
        text = {
            Column {
                Text("El efectivo regresará al cálculo. Para corregirlo, registra después el gasto correcto.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("Motivo obligatorio") },
                )
            }
        },
        confirmButton = { Button(onClick = { if (reason.isNotBlank()) onConfirm(reason) }) { Text("Anular") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatAuditTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
