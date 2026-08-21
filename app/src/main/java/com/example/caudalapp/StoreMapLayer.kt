package com.example.caudalapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreMarkerState

@Composable
fun StoreMapLayer(
    stores: List<Store>,
    center: GeoPoint,
    placementMode: Boolean,
    showPlacementHint: Boolean = true,
    onCenterChanged: (GeoPoint) -> Unit,
    onStoreSelected: (Store) -> Unit,
    markerState: (Store) -> StoreMarkerState,
    moneyDue: (Store) -> Int,
    unitsPending: (Store) -> Int,
    focusLocation: GeoPoint? = null,
    focusRequestId: Int = 0,
    onLocationPermissionGranted: () -> Unit = {},
    automaticFollow: Boolean = true,
    gpsIntervalSeconds: Int = 1,
    mapStyle: AppMapStyle = AppMapStyle.LIBERTY,
    modifier: Modifier = Modifier,
) {
    val mapController = remember { CaudalMapController() }
    LaunchedEffect(placementMode) {
        mapController.setPlacementMode(placementMode)
    }
    LaunchedEffect(focusRequestId) {
        focusLocation?.let(mapController::focusOn)
    }
    val mapStores = stores.map { store ->
        MapStorePoint(
            id = store.id,
            name = store.name,
            location = store.location,
            state = markerState(store),
            moneyDue = moneyDue(store),
            unitsPending = unitsPending(store),
        )
    }
    Box(modifier = modifier) {
        MapLibreMapView(
            initialCenter = center,
            onCenterChanged = onCenterChanged,
            controller = mapController,
            stores = mapStores,
            onStoreSelected = { storeId -> stores.firstOrNull { it.id == storeId }?.let(onStoreSelected) },
            onLocationPermissionGranted = onLocationPermissionGranted,
            automaticFollow = automaticFollow,
            gpsIntervalSeconds = gpsIntervalSeconds,
            mapStyle = mapStyle,
            modifier = Modifier.fillMaxSize(),
        )

        if (placementMode) {
            Box(
                modifier = Modifier.align(Alignment.Center).size(24.dp).shadow(8.dp, CircleShape)
                    .background(Color(0xFFE64646), CircleShape),
            )
        }

        Button(
            onClick = { mapController.followCurrentLocation() },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 104.dp).size(52.dp),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("◎", fontSize = 20.sp) }

        if (placementMode && showPlacementHint) {
            Text(
                "Mueve el mapa debajo de la mira\n${"%.5f".format(center.latitude)}, ${"%.5f".format(center.longitude)}",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp)
                    .background(Color.White.copy(alpha = .94f), RoundedCornerShape(16.dp)).padding(12.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun StoreNameDialog(
    location: GeoPoint,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("Nueva tienda", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    modifier = Modifier.padding(top = 10.dp),
                    label = { Text("Nombre de la tienda") },
                    isError = error,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("Teléfono (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                )
                if (error) Text("Escribe el nombre", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = true else onSave(name, phone.takeIf(String::isNotBlank))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
