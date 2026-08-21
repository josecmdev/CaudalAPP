package com.example.caudalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.caudalapp.ui.theme.CaudalAPPTheme
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.persistence.CaudalRestoredState
import com.example.caudalapp.persistence.CaudalStateStore
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
        appState.persist()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        appState.reloadPersistence()
    }
}

enum class HomeDestination(val title: String, val description: String, val symbol: String) {
    NEW_ROUTE("Nueva ruta", "Prepara sencillo e inventario", "→"),
    HISTORY("Historial", "Consulta rutas y cierres anteriores", "↺"),
    ACCOUNTS("Cuentas pendientes", "Deudas, envases y entregas", "Q"),
    STORES("Tiendas", "Administra los puntos del mapa", "⌂"),
    PRODUCTS("Productos", "Edita productos e imágenes", "+"),
    OFFLINE_MAP("Mapa sin internet", "Descarga tu zona de trabajo", "↓"),
    SETTINGS("Ajustes", "Personaliza y prepara la aplicación", "⚙"),
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

    fun attachPersistence(file: File) {
        if (stateStore != null) return
        stateStore = CaudalStateStore(file)
        reloadPersistence()
    }

    fun reloadPersistence() {
        stateStore?.load()?.let(::applyRestoredState)
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

    fun persist() {
        stateStore?.save(
            CaudalRestoredState(
                activeRoute = activeRoute,
                stores = stores,
                completedRoutes = completedRoutes.toList(),
                outstandingAccounts = outstandingAccounts,
                products = products,
                accountAdjustments = accountAdjustments.toList(),
            ),
        )
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
    LaunchedEffect(appState) {
        appState.completionTransitionEvents().collect { completionTransitionId++ }
    }
    Box(modifier = Modifier.fillMaxSize()) {
      Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (appState.mapVisible && appState.activeRoute != null) {
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
        } else if (appState.mapVisible) {
            HomeMapScreen(
                stores = appState.stores,
                accounts = appState.outstandingAccounts,
                previousRoutes = appState.completedRoutes,
                onStartRoute = {
                    appState.destination = HomeDestination.NEW_ROUTE
                    appState.mapVisible = false
                },
                onExitMap = {
                    mapMenuOpen = true
                },
                onStateChanged = appState::persist,
                onAccountsChanged = { updated, adjustment ->
                    appState.outstandingAccounts = updated
                    appState.accountAdjustments += adjustment
                    appState.persist()
                },
                productName = { id -> appState.products.get(id)?.name ?: id },
                settings = appState.settings,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == null) {
            HomeScreen(
                routeActive = appState.activeRoute != null,
                onOpenMap = {
                    appState.destination = null
                    appState.mapVisible = true
                },
                onFinishRoute = {
                    appState.routeCloseRequested = true
                    appState.destination = null
                    appState.mapVisible = true
                },
                onDestinationSelected = {
                    appState.storeFocusId = null
                    appState.destination = it
                },
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
                onDeleteRoute = { record ->
                    record.trackId?.let { RouteTrackingService.trackStore(context).delete(it) }
                    appState.completedRoutes.remove(record)
                    appState.persist()
                },
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        } else if (appState.destination == HomeDestination.ACCOUNTS) {
            PendingAccountsScreen(
                stores = appState.stores,
                accounts = appState.outstandingAccounts,
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
                accounts = appState.outstandingAccounts,
                initialFocus = appState.storeFocusId?.let { appState.stores.get(it)?.location },
                onBack = returnToMap,
                onStateChanged = appState::persist,
                onAccountsChanged = { updated, adjustment ->
                    appState.outstandingAccounts = updated
                    appState.accountAdjustments += adjustment
                    appState.persist()
                },
                productName = { id -> appState.products.get(id)?.name ?: id },
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
        } else if (appState.destination == HomeDestination.SETTINGS) {
            SettingsScreen(
                settings = appState.settings,
                onSettingsChanged = appState::updateSettings,
                onExportDiagnostics = { DiagnosticReporter.share(activityContext) },
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        } else {
            FirstIterationPlaceholder(
                destination = appState.destination!!,
                onBack = returnToMap,
                modifier = Modifier.padding(padding),
            )
        }
      }
      if (mapMenuOpen) {
          MapOptionsDialog(
              routeActive = appState.activeRoute != null,
              onDismiss = { mapMenuOpen = false },
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
                    TextButton(onClick = onDismiss) { Text("Volver al mapa") }
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
    if (opacity.value > 0f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val water = Color(0xFF0878E8).copy(alpha = opacity.value)
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
private fun HomeScreen(
    routeActive: Boolean,
    onOpenMap: () -> Unit,
    onFinishRoute: () -> Unit,
    onDestinationSelected: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val destinations = HomeDestination.entries.filterNot { it == HomeDestination.NEW_ROUTE }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (landscape) 34.dp else 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("C", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Caudal App", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Ventas y rutas en un solo lugar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (routeActive) Color(0xFFDDF7E7) else MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            if (routeActive) "● Ruta activa" else "Listo para salir",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            color = if (routeActive) Color(0xFF177A45) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryRouteButton(
                    routeActive = routeActive,
                    onClick = if (routeActive) onOpenMap else {
                        { onDestinationSelected(HomeDestination.NEW_ROUTE) }
                    },
                )
                if (routeActive) {
                    TextButton(onClick = onFinishRoute, modifier = Modifier.fillMaxWidth()) {
                        Text("Finalizar ruta activa", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (landscape) {
                    destinations.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { item ->
                                DestinationCard(
                                    item = item,
                                    onClick = { onDestinationSelected(item) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    destinations.forEach { item ->
                        DestinationCard(item = item, onClick = { onDestinationSelected(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryRouteButton(routeActive: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(if (routeActive) "Volver al mapa" else "Iniciar ruta", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (routeActive) "La ruta continúa activa" else "Preparar sencillo e inventario",
                color = Color.White.copy(alpha = .82f),
            )
        }
        Text("→", fontSize = 28.sp)
    }
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
                Text(item.symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FirstIterationPlaceholder(
    destination: HomeDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                destination.title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(72.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(destination.symbol, color = MaterialTheme.colorScheme.primary, fontSize = 30.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text(destination.description, textAlign = TextAlign.Center)
                Text(
                    "Base preparada para la siguiente iteración",
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun HomePreview() {
    CaudalAPPTheme {
        HomeScreen(
            routeActive = false,
            onOpenMap = {},
            onFinishRoute = {},
            onDestinationSelected = {},
        )
    }
}
