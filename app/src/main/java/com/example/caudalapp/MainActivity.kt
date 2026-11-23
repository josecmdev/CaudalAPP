package com.example.caudalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.ui.theme.CaudalAPPTheme
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.StoreMarkerPolicy
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.persistence.CaudalRestoredState
import com.example.caudalapp.persistence.CaudalStateStore
import com.example.caudalapp.persistence.CaudalStateWriter
import com.example.caudalapp.persistence.detachedCopy
import androidx.lifecycle.ViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val appState by viewModels<CaudalViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appState.attachPersistence(File(filesDir, "caudal-state-v1.json"))
        appState.attachSettings(applicationContext)
        enableEdgeToEdge()
        setContent { CaudalAPPTheme(appState.settings.theme) { CaudalApp(appState) } }
    }

    override fun onStop() {
        appState.persistNow()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        appState.reloadPersistence()
    }
}

enum class HomeDestination(val title: String, val description: String, val icon: ImageVector?) {
    NEW_ROUTE("Nueva ruta", "Prepara sencillo e inventario", Icons.AutoMirrored.Filled.ArrowForward),
    HISTORY("Historial", "Consulta rutas y cierres anteriores", Icons.Default.DateRange),
    ACCOUNTS("Cuentas pendientes", "Deudas, envases y entregas", null),
    STORES("Tiendas", "Administra los puntos del mapa", Icons.Default.Home),
    PRODUCTS("Productos", "Edita productos e imágenes", Icons.Default.Edit),
    OFFLINE_MAP("Mapa sin internet", "Descarga tu zona de trabajo", Icons.Default.Place),
    SETTINGS("Ajustes", "Personaliza y prepara la aplicación", Icons.Default.Settings),
}

class CaudalViewModel : ViewModel() {
    var destination by mutableStateOf<HomeDestination?>(null)
    var mapVisible by mutableStateOf(true)
    var routeCloseRequested by mutableStateOf(false)
    private val completionTransitions = Channel<Unit>(capacity = Channel.BUFFERED)
    var activeRoute by mutableStateOf<ActiveRoute?>(null)
    var stores by mutableStateOf(StoreDirectory())
        private set
    val completedRoutes = mutableStateListOf<CompletedRouteRecord>()
    var outstandingAccounts by mutableStateOf<List<StoreAccountState>>(emptyList())
    val accountAdjustments = mutableStateListOf<StoreAccountAdjustment>()
    var products by mutableStateOf(ProductDirectory())
        private set
    var storeFocusId by mutableStateOf<String?>(null)
    private var stateStore: CaudalStateStore? = null
    var settings by mutableStateOf(AppSettings())
        private set
    private var settingsStore: AppSettingsStore? = null

    private val externalChangeListener: () -> Unit = {
        android.os.Handler(android.os.Looper.getMainLooper()).post(::reloadPersistence)
    }

    fun attachPersistence(file: File) {
        if (stateStore != null) return
        stateStore = CaudalStateStore(file)
        CaudalStateWriter.addExternalChangeListener(externalChangeListener)
        reloadPersistence()
    }

    fun reloadPersistence() {
        val store = stateStore ?: return
        CaudalStateWriter.runBlocking { store.load() }?.let(::applyRestoredState)
    }

    override fun onCleared() {
        CaudalStateWriter.removeExternalChangeListener(externalChangeListener)
    }

    private fun applyRestoredState(restored: CaudalRestoredState) {
        activeRoute = restored.activeRoute
        stores = restored.stores
        completedRoutes.clear()
        completedRoutes.addAll(restored.completedRoutes)
        outstandingAccounts = restored.outstandingAccounts
        accountAdjustments.clear()
        accountAdjustments.addAll(restored.accountAdjustments)
        products = restored.products
    }

    /** Guarda en segundo plano para no congelar la pantalla en cada venta. */
    fun persist() {
        val store = stateStore ?: return
        val snapshot = currentState().detachedCopy()
        CaudalStateWriter.enqueue { store.save(snapshot) }
    }

    /** Guarda y espera; para cuando la app pasa a segundo plano y Android podría cerrarla. */
    fun persistNow() {
        val store = stateStore ?: return
        val snapshot = currentState()
        CaudalStateWriter.runBlocking { store.save(snapshot) }
    }

    private fun currentState() = CaudalRestoredState(
        activeRoute = activeRoute,
        stores = stores,
        completedRoutes = completedRoutes.toList(),
        outstandingAccounts = outstandingAccounts,
        products = products,
        accountAdjustments = accountAdjustments.toList(),
    )

    private var accountsRevision by mutableIntStateOf(0)

    /** Con ruta activa, las cuentas vivas son las de la ruta; las guardadas se reemplazan al cerrarla. */
    val currentAccounts: List<StoreAccountState>
        get() {
            accountsRevision
            return activeRoute?.ledger?.outstandingAccountStates() ?: outstandingAccounts
        }

    fun applyAccountChange(updated: List<StoreAccountState>, adjustment: StoreAccountAdjustment) {
        val route = activeRoute
        if (route == null) outstandingAccounts = updated else route.ledger.applyAccountAdjustment(adjustment)
        accountAdjustments += adjustment
        accountsRevision++
        persist()
    }

    fun attachSettings(context: android.content.Context) {
        if (settingsStore != null) return
        settingsStore = AppSettingsStore(context)
        settings = settingsStore!!.load()
    }

    fun updateSettings(updated: AppSettings) {
        settings = updated
        settingsStore?.save(updated)
    }

    fun showCompletionTransition() {
        completionTransitions.trySend(Unit)
    }

    fun completionTransitionEvents() = completionTransitions.receiveAsFlow()
}

@Composable
private fun CaudalApp(appState: CaudalViewModel) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    var completionTransitionId by remember { mutableIntStateOf(0) }
    var mapMenuOpen by rememberSaveable { mutableStateOf(false) }
    val returnToMap = {
        appState.storeFocusId = null
        appState.destination = null
        appState.mapVisible = true
    }
    var exitConfirmOpen by remember { mutableStateOf(false) }
    LaunchedEffect(appState) {
        appState.completionTransitionEvents().collect { completionTransitionId++ }
    }
    BackHandler {
        if (appState.mapVisible) exitConfirmOpen = true else returnToMap()
    }
    if (exitConfirmOpen) {
        AlertDialog(
            onDismissRequest = { exitConfirmOpen = false },
            title = { Text("¿Salir de Caudal App?") },
            text = {
                if (appState.activeRoute != null) Text("La ruta sigue activa y el GPS sigue registrando.")
            },
            confirmButton = {
                TextButton(onClick = {
                    exitConfirmOpen = false
                    (activityContext as? android.app.Activity)?.finish()
                }) { Text("Salir") }
            },
            dismissButton = { Button(onClick = { exitConfirmOpen = false }) { Text("Quedarme") } },
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
      Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        val showMap = appState.mapVisible || appState.destination == null
        if (showMap && appState.activeRoute != null) {
            val currentRoute = appState.activeRoute!!
            ActiveRouteScreen(
                route = currentRoute,
                stores = appState.stores,
                previousRoutes = appState.completedRoutes,
                onExitMap = {
                    mapMenuOpen = true
                },
                closeRequested = appState.routeCloseRequested,
                onCloseRequestConsumed = { appState.routeCloseRequested = false },
                onRouteFinished = { closeResult ->
                    val trackId = RouteTrackingService.routeId(currentRoute.startedAtEpochMillis)
                    val activeDurationMillis = RouteTrackingService.elapsedMillis(context, trackId)
                    RouteTrackingService.stop(context)
                    val trackPointCount = RouteTrackingService.trackStore(context).count(trackId)
                    appState.completedRoutes += CompletedRouteRecord(
                        routeName = currentRoute.name,
                        startedAtEpochMillis = currentRoute.startedAtEpochMillis,
                        finishedAtEpochMillis = System.currentTimeMillis(),
                        summary = closeResult.summary,
                        trackId = trackId,
                        trackPointCount = trackPointCount,
                        sales = currentRoute.ledger.sales,
                        auditEntries = currentRoute.ledger.auditEntries,
                        activeDurationMillis = activeDurationMillis,
                        countedCash = closeResult.countedCash,
                        countedStock = closeResult.countedStock,
                        expectedStock = currentRoute.ledger.products().associate { product ->
                            product.id to currentRoute.ledger.availableStock(product.id)
                        },
                    )
                    appState.outstandingAccounts = currentRoute.ledger.outstandingAccountStates()
                    appState.activeRoute = null
                    appState.destination = null
                    appState.mapVisible = true
                    appState.showCompletionTransition()
                    appState.persist()
                },
                onRouteDiscarded = {
                    val trackId = RouteTrackingService.routeId(currentRoute.startedAtEpochMillis)
                    RouteTrackingService.stop(context)
                    RouteTrackingService.trackStore(context).delete(trackId)
                    appState.activeRoute = null
                    appState.destination = null
                    appState.mapVisible = true
                    appState.showCompletionTransition()
                    appState.persist()
                },
                onStateChanged = appState::persist,
                onLocationPermissionGranted = {
                    val trackId = RouteTrackingService.routeId(currentRoute.startedAtEpochMillis)
                    runCatching {
                        RouteTrackingService.start(context, trackId, appState.settings.gpsIntervalSeconds)
                    }
                },
                settings = appState.settings,
                modifier = Modifier.padding(padding),
            )
        } else if (showMap) {
            HomeMapScreen(
                stores = appState.stores,
                accounts = appState.currentAccounts,
                previousRoutes = appState.completedRoutes,
                onStartRoute = {
                    appState.destination = HomeDestination.NEW_ROUTE
                    appState.mapVisible = false
                },
                onExitMap = {
                    mapMenuOpen = true
                },
                onStateChanged = appState::persist,
                onAccountsChanged = appState::applyAccountChange,
                productName = { id -> appState.products.get(id)?.name ?: id },
                settings = appState.settings,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.NEW_ROUTE) {
            RoutePreparationScreen(
                products = appState.products.activeProducts(),
                onBack = {
                    appState.storeFocusId = null
                    appState.destination = null
                    appState.mapVisible = true
                },
                onRouteStarted = { route ->
                    route.ledger.registerProducts(appState.products.allProducts())
                    route.ledger.importAccounts(appState.outstandingAccounts)
                    appState.activeRoute = route
                    appState.destination = null
                    appState.mapVisible = true
                    appState.persist()
                },
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.HISTORY) {
            RouteHistoryScreen(
                routes = appState.completedRoutes,
                stores = appState.stores,
                productName = { id -> appState.products.get(id)?.name ?: id },
                onArchiveChanged = { record, archived ->
                    val index = appState.completedRoutes.indexOf(record)
                    if (index >= 0) {
                        appState.completedRoutes[index] = record.copy(archived = archived)
                        appState.persist()
                    }
                },
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.ACCOUNTS) {
            PendingAccountsScreen(
                stores = appState.stores,
                accounts = appState.currentAccounts,
                productName = { id -> appState.products.get(id)?.name ?: id },
                onSelectStore = { store ->
                    appState.storeFocusId = store.id
                    appState.destination = HomeDestination.STORES
                },
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.STORES) {
            StoresScreen(
                stores = appState.stores,
                accounts = appState.currentAccounts,
                initialFocus = appState.storeFocusId?.let { appState.stores.get(it)?.location },
                onBack = returnToMap,
                onStateChanged = appState::persist,
                onAccountsChanged = appState::applyAccountChange,
                productName = { id -> appState.products.get(id)?.name ?: id },
                daysSinceLastSaleByStore = remember(appState.completedRoutes.toList()) {
                    StoreMarkerPolicy.daysSinceLastSaleByStore(appState.completedRoutes.flatMap(CompletedRouteRecord::sales))
                },
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.PRODUCTS) {
            ProductsScreen(
                directory = appState.products,
                onBack = returnToMap,
                onStateChanged = appState::persist,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.OFFLINE_MAP) {
            OfflineMapScreen(
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        } else {
            SettingsScreen(
                settings = appState.settings,
                onSettingsChanged = appState::updateSettings,
                onExportDiagnostics = { DiagnosticReporter.share(activityContext) },
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        }
      }
      if (mapMenuOpen) {
          MapOptionsDialog(
              routeActive = appState.activeRoute != null,
              onDismiss = { mapMenuOpen = false },
              onFinishRoute = {
                  mapMenuOpen = false
                  appState.routeCloseRequested = true
              },
              onDestinationSelected = { destination ->
                  mapMenuOpen = false
                  appState.storeFocusId = null
                  appState.destination = destination
                  appState.mapVisible = false
              },
          )
      }
      CompletionDropTransition(completionTransitionId)
    }
}

@Composable
private fun MapOptionsDialog(
    routeActive: Boolean,
    onDismiss: () -> Unit,
    onFinishRoute: () -> Unit,
    onDestinationSelected: (HomeDestination) -> Unit,
) {
    val destinations = HomeDestination.entries.filter { it != HomeDestination.NEW_ROUTE }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(.9f).fillMaxHeight(.88f).widthIn(max = 920.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Caudal App", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (routeActive) "La ruta sigue activa en segundo plano" else "Mapa de trabajo activo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Finalizar vive aquí, lejos del mapa, para que no se toque por accidente a media ruta.
                        if (routeActive) {
                            TextButton(onClick = onFinishRoute) {
                                Text("Finalizar ruta", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(onClick = onDismiss) { Text("Volver al mapa") }
                    }
                }
                Spacer(Modifier.height(18.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val columns = if (maxWidth >= 700.dp) 2 else 1
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        destinations.chunked(columns).forEach { items ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items.forEach { item ->
                                    DestinationCard(
                                        item = item,
                                        onClick = { onDestinationSelected(item) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (items.size < columns) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionDropTransition(trigger: Int) {
    val progress = remember { Animatable(0f) }
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        opacity.snapTo(1f)
        progress.animateTo(1f, tween(1_050, easing = FastOutSlowInEasing))
        delay(80)
        opacity.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
    }
    val waterColor = MaterialTheme.colorScheme.primary
    if (opacity.value > 0f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val water = waterColor.copy(alpha = opacity.value)
            val p = progress.value
            val impactY = size.height * .42f
            if (p < .55f) {
                val fall = (p / .55f).coerceIn(0f, 1f)
                val radius = 22f + 54f * fall
                val dropCenter = Offset(size.width / 2f, -90f + (impactY + 90f) * fall)
                drawPath(waterDropPath(dropCenter, radius), water)
                if (fall > .25f) {
                    drawCircle(
                        color = water.copy(alpha = water.alpha * .38f),
                        radius = radius * .22f,
                        center = Offset(dropCenter.x, dropCenter.y - radius * 1.9f),
                    )
                }
            } else {
                val spread = ((p - .55f) / .45f).coerceIn(0f, 1f)
                val easedSpread = FastOutSlowInEasing.transform(spread)
                val radius = 76f + size.maxDimension * 1.18f * easedSpread
                drawCircle(water, radius, Offset(size.width / 2f, impactY))
                if (spread < .35f) {
                    drawOval(
                        color = Color.White.copy(alpha = (.24f * (1f - spread / .35f)) * opacity.value),
                        topLeft = Offset(size.width / 2f - 110f, impactY - 18f),
                        size = androidx.compose.ui.geometry.Size(220f, 36f),
                    )
                }
            }
        }
    }
}

private fun waterDropPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius * 1.55f)
    cubicTo(
        center.x - radius * .18f,
        center.y - radius,
        center.x - radius,
        center.y - radius * .25f,
        center.x - radius,
        center.y + radius * .35f,
    )
    cubicTo(
        center.x - radius,
        center.y + radius,
        center.x - radius * .48f,
        center.y + radius * 1.32f,
        center.x,
        center.y + radius * 1.32f,
    )
    cubicTo(
        center.x + radius * .48f,
        center.y + radius * 1.32f,
        center.x + radius,
        center.y + radius,
        center.x + radius,
        center.y + radius * .35f,
    )
    cubicTo(
        center.x + radius,
        center.y - radius * .25f,
        center.x + radius * .18f,
        center.y - radius,
        center.x,
        center.y - radius * 1.55f,
    )
    close()
}

@Composable
private fun DestinationCard(
    item: HomeDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(86.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Cuentas usa la Q de quetzal: no hay ícono de moneda local.
                item.icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    ?: Text("Q", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}
