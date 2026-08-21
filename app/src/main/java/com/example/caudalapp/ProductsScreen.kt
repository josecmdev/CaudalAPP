package com.example.caudalapp

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.ProductChangeResult
import com.example.caudalapp.domain.ProductDirectory

@Composable
fun ProductsScreen(
    directory: ProductDirectory,
    onBack: () -> Unit,
    onStateChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    val products = remember(revision) { directory.allProducts() }

    if (creating || editing != null) {
        ProductEditorDialog(
            product = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, suggested, returnable, imageUri ->
                val result = editing?.let {
                    directory.updateProduct(it.id, name, suggested, returnable, imageUri)
                } ?: directory.addProduct(name, suggested, returnable, imageUri)
                if (result is ProductChangeResult.Success) {
                    creating = false
                    editing = null
                    revision++
                    onStateChanged()
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Column(modifier = Modifier.weight(1f)) {
                Text("Productos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Catálogo local", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { creating = true }) { Text("+ Nuevo") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products, key = Product::id) { product ->
                ProductCard(
                    product = product,
                    onEdit = { editing = product },
                    onArchiveToggle = {
                        val changed = if (product.archived) {
                            directory.restoreProduct(product.id)
                        } else {
                            directory.archiveProduct(product.id)
                        }
                        if (changed) {
                            revision++
                            onStateChanged()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onEdit: () -> Unit, onArchiveToggle: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ProductImage(product = product, modifier = Modifier.size(58.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    buildString {
                        append(product.defaultSuggestedTotal?.let { "Sugerido Q$it" } ?: "Precio libre")
                        if (product.returnable) append(" · Envase retornable")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (product.archived) Text("Archivado", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onArchiveToggle) {
                Text(if (product.archived) "Restaurar" else "Archivar")
            }
        }
    }
}

@Composable
private fun ProductImage(product: Product, modifier: Modifier = Modifier) {
    val uri = product.imageUri
    if (uri == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(product.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    } else {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { image -> image.setImageURI(Uri.parse(uri)) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ProductEditorDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Boolean, String?) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var suggested by remember(product?.id) { mutableStateOf(product?.defaultSuggestedTotal?.toString().orEmpty()) }
    var returnable by remember(product?.id) { mutableStateOf(product?.returnable ?: false) }
    var imageUri by remember(product?.id) { mutableStateOf(product?.imageUri) }
    var error by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imageUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(if (product == null) "Nuevo producto" else "Editar producto", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(
                    suggested,
                    { suggested = it.filter(Char::isDigit).take(7); error = null },
                    label = { Text("Precio sugerido (opcional)") },
                    prefix = { Text("Q ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Producto retornable", fontWeight = FontWeight.SemiBold)
                        Text("Controla envases llenos y vacíos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = returnable, onCheckedChange = { returnable = it })
                }
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (imageUri == null) "Elegir imagen" else "Cambiar imagen")
                }
                if (imageUri != null) {
                    TextButton(onClick = { imageUri = null }, modifier = Modifier.fillMaxWidth()) { Text("Quitar imagen") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = "Escribe el nombre" else onSave(
                    name,
                    suggested.toIntOrNull(),
                    returnable,
                    imageUri,
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
