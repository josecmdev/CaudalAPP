package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.ExpenseCategory

@Composable
fun ExpenseDialog(
    route: ActiveRoute,
    onDismiss: () -> Unit,
    onRegistered: (ExpenseCategory, Int) -> Unit,
) {
    var category by remember { mutableStateOf(ExpenseCategory.FUEL) }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun save() {
        val amount = amountText.toIntOrNull() ?: 0
        when {
            amount <= 0 -> error = "Escribe el monto del gasto"
            amount > route.ledger.cashOnHand -> error = "No hay suficiente efectivo disponible"
            else -> {
                route.ledger.registerExpense(category, amount)
                onRegistered(category, amount)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Registrar gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ExpenseCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.displayName) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(7); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monto en quetzales") },
                    prefix = { Text("Q") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); save() }),
                    isError = error != null,
                    supportingText = error?.let { message -> ({ Text(message) }) },
                )
            }
        },
        confirmButton = { Button(onClick = ::save) { Text("Guardar gasto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
