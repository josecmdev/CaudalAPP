package com.example.caudalapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreDirectory

@Composable
fun PendingAccountsScreen(
    stores: StoreDirectory,
    accounts: List<StoreAccountState>,
    productName: (String) -> String = ::defaultProductName,
    onSelectStore: (Store) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Cuentas pendientes",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(72.dp))
        }
        AccountsList(stores, accounts, productName = productName, onSelectStore = onSelectStore)
    }
}

@Composable
fun PendingAccountsDialog(
    stores: StoreDirectory,
    accounts: List<StoreAccountState>,
    productName: (String) -> String = ::defaultProductName,
    onDismiss: () -> Unit,
    onSelectStore: (Store) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Cuentas pendientes", fontWeight = FontWeight.Bold) },
        text = {
            AccountsList(
                stores = stores,
                accounts = accounts,
                productName = productName,
                onSelectStore = onSelectStore,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun AccountsList(
    stores: StoreDirectory,
    accounts: List<StoreAccountState>,
    modifier: Modifier = Modifier.fillMaxSize(),
    onSelectStore: ((Store) -> Unit)? = null,
    productName: (String) -> String = ::defaultProductName,
) {
    if (accounts.isEmpty()) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No hay cuentas pendientes", fontWeight = FontWeight.SemiBold)
            Text("Deudas, envases y entregas aparecerán aquí", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = StoreAccountState::storeId) { account ->
                val store = stores.get(account.storeId)
                PendingAccountCard(
                    storeName = store?.name ?: "Tienda archivada",
                    account = account,
                    productName = productName,
                    onClick = store?.let { selected -> onSelectStore?.let { callback -> ({ callback(selected) }) } },
                )
            }
        }
    }
}

@Composable
private fun PendingAccountCard(
    storeName: String,
    account: StoreAccountState,
    onClick: (() -> Unit)?,
    productName: (String) -> String,
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(storeName, fontWeight = FontWeight.Bold)
                val money = account.debts.sumOf { it.remainingAmount } + account.pendingPayments.values.sum()
                if (money > 0) Text("Q$money", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            account.pendingDeliveries.filterValues { it > 0 }.forEach { (productId, quantity) ->
                Text("Faltan $quantity ${productName(productId)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            account.pendingContainers.filterValues { it > 0 }.forEach { (productId, quantity) ->
                Text("Debe $quantity envases de ${productName(productId)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Text("Ver en el mapa →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun defaultProductName(productId: String): String = ProductCatalog.find(productId)?.name ?: productId
