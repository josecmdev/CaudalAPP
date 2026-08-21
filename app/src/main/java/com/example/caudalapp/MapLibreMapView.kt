package com.example.caudalapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.StoreMarkerState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val CAUDAL_NAVIGATION_ZOOM = 16.2
private const val CAUDAL_MAP_LOG_TAG = "CaudalMap"
private const val STORE_SOURCE_ID = "caudal-store-source"
private const val STORE_LAYER_ID = "caudal-store-layer"
private const val STORE_CLUSTER_LAYER_ID = "caudal-store-clusters"
private const val STORE_CLUSTER_COUNT_LAYER_ID = "caudal-store-cluster-count"

data class MapStorePoint(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val state: StoreMarkerState,
    val moneyDue: Int,
    val unitsPending: Int,
)

class CaudalMapController {
    internal var map: MapLibreMap? = null
    private var context: android.content.Context? = null
    private var style: Style? = null
    private var latestStores: List<MapStorePoint> = emptyList()
    private var onStoreSelected: (String) -> Unit = {}
    private var placementMode = false
    private var automaticFollow = true
    private var navigationTilt = 24.0
    private var resumeFollowAtMillis = 0L
    private var pendingFocus: GeoPoint? = null
    private var onCenterChanged: (GeoPoint) -> Unit = {}
    private var clickListener: MapLibreMap.OnMapClickListener? = null
    private var cameraIdleListener: MapLibreMap.OnCameraIdleListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val movementMonitor = object : Runnable {
        override fun run() {
            if (automaticFollow && !placementMode) updateMovementAwareTracking()
            mainHandler.postDelayed(this, MOVEMENT_CHECK_INTERVAL_MILLIS)
        }
    }

    internal fun attach(
        map: MapLibreMap,
        context: android.content.Context,
        onCenterChanged: (GeoPoint) -> Unit,
    ) {
        if (this.map !== map) detach()
        this.map = map
        this.context = context
        this.onCenterChanged = onCenterChanged
        mainHandler.removeCallbacks(movementMonitor)
        mainHandler.post(movementMonitor)
        if (clickListener == null) {
            clickListener = MapLibreMap.OnMapClickListener { coordinate ->
                val screenPoint = map.projection.toScreenLocation(coordinate)
                val store = map.queryRenderedFeatures(screenPoint, STORE_LAYER_ID).firstOrNull()
                val storeId = store?.getStringProperty("storeId")
                if (!storeId.isNullOrBlank()) {
                    onStoreSelected(storeId)
                    true
                } else {
                    val cluster = map.queryRenderedFeatures(screenPoint, STORE_CLUSTER_LAYER_ID).firstOrNull()
                    if (cluster != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(coordinate, (map.cameraPosition.zoom + 2.0).coerceAtMost(17.0)),
                            450,
                        )
                        true
                    } else {
                        false
                    }
                }
            }
            cameraIdleListener = MapLibreMap.OnCameraIdleListener {
                map.cameraPosition.target?.let { target ->
                    this.onCenterChanged(GeoPoint(target.latitude, target.longitude))
                }
                scheduleAutomaticFollow()
            }
            map.addOnMapClickListener(clickListener!!)
            map.addOnCameraIdleListener(cameraIdleListener!!)
        }
    }

    internal fun detach() {
        mainHandler.removeCallbacksAndMessages(null)
        val currentMap = map
        clickListener?.let { listener -> runCatching { currentMap?.removeOnMapClickListener(listener) } }
        cameraIdleListener?.let { listener -> runCatching { currentMap?.removeOnCameraIdleListener(listener) } }
        this.map = null
        context = null
        style = null
        clickListener = null
        cameraIdleListener = null
    }

    internal fun onStyleLoaded(style: Style) {
        this.style = style
        renderStores()
        pendingFocus?.let(::focusOn)
    }

    fun updateStores(
        stores: List<MapStorePoint>,
        onSelected: (String) -> Unit,
        onCenterChanged: (GeoPoint) -> Unit,
    ) {
        onStoreSelected = onSelected
        this.onCenterChanged = onCenterChanged
        if (stores == latestStores) return
        latestStores = stores
        renderStores()
    }

    fun setAutomaticFollow(enabled: Boolean) {
        automaticFollow = enabled
        mainHandler.removeCallbacks(movementMonitor)
        if (enabled) mainHandler.post(movementMonitor)
    }

    fun setMapStyle(mapStyle: AppMapStyle) {
        navigationTilt = if (mapStyle == AppMapStyle.THREE_D) 52.0 else 24.0
    }

    private fun renderStores() {
        val currentContext = context ?: return
        val currentStyle = style ?: return
        val features = latestStores.map { store ->
            val badge = when {
                store.moneyDue > 0 -> "Debe Q${store.moneyDue}"
                store.unitsPending > 0 -> "${store.unitsPending} pendientes"
                else -> null
            }
            val imageId = storeCompositeImageId(store, badge)
            if (currentStyle.getImage(imageId) == null) {
                currentStyle.addImage(imageId, createStoreCompositeBitmap(currentContext, store, badge))
            }
            Feature.fromGeometry(Point.fromLngLat(store.location.longitude, store.location.latitude)).apply {
                addStringProperty("storeId", store.id)
                addStringProperty("iconId", imageId)
            }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val existingSource = currentStyle.getSourceAs<GeoJsonSource>(STORE_SOURCE_ID)
        if (existingSource != null) {
            existingSource.setGeoJson(collection)
            return
        }
        currentStyle.addSource(
            GeoJsonSource(
                STORE_SOURCE_ID,
                collection,
                GeoJsonOptions().withCluster(true).withClusterMaxZoom(14).withClusterRadius(55),
            ),
        )
        currentStyle.addLayer(
            CircleLayer(STORE_CLUSTER_LAYER_ID, STORE_SOURCE_ID).withProperties(
                circleColor("#0878E8"),
                circleRadius(22f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(3f),
            ).withFilter(Expression.has("point_count")),
        )
        currentStyle.addLayer(
            SymbolLayer(STORE_CLUSTER_COUNT_LAYER_ID, STORE_SOURCE_ID).withProperties(
                textField("{point_count_abbreviated}"),
                textSize(14f),
                textColor("#FFFFFF"),
            ).withFilter(Expression.has("point_count")),
        )
        currentStyle.addLayer(
            SymbolLayer(STORE_LAYER_ID, STORE_SOURCE_ID).withProperties(
                iconImage(Expression.get("iconId")),
                iconSize(.72f),
                iconOffset(arrayOf(0f, -58f)),
                iconAllowOverlap(true),
            ).withFilter(Expression.not(Expression.has("point_count"))),
        )
    }

    @SuppressLint("MissingPermission")
    fun followCurrentLocation() {
        resumeFollowAtMillis = 0L
        map?.locationComponent?.let { location ->
            if (!location.isLocationComponentActivated) return
            val mode = if (vehicleIsMoving()) CameraMode.TRACKING_GPS else CameraMode.TRACKING
            if (location.cameraMode == mode) return
            location.setCameraMode(
                mode,
                650L,
                CAUDAL_NAVIGATION_ZOOM,
                null,
                navigationTilt,
                null,
            )
        }
    }

    fun setPlacementMode(active: Boolean) {
        placementMode = active
        val location = map?.locationComponent ?: return
        if (!location.isLocationComponentActivated) return
        if (active) location.cameraMode = CameraMode.NONE else followCurrentLocation()
    }

    internal fun restoreCameraMode() = setPlacementMode(placementMode)

    fun focusOn(location: GeoPoint) {
        pendingFocus = location
        val currentMap = map ?: return
        pendingFocus = null
        val component = currentMap.locationComponent
        if (component.isLocationComponentActivated) component.cameraMode = CameraMode.NONE
        currentMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17.0),
            750,
        )
    }

    private fun scheduleAutomaticFollow() {
        if (!automaticFollow) return
        val location = map?.locationComponent ?: return
        if (placementMode || !location.isLocationComponentActivated ||
            location.cameraMode == CameraMode.TRACKING_GPS || location.cameraMode == CameraMode.TRACKING
        ) return
        // Mientras el vehículo está detenido se respeta la posición elegida por
        // el usuario. El monitor recupera el seguimiento al detectar movimiento.
        resumeFollowAtMillis = if (vehicleIsMoving()) {
            android.os.SystemClock.uptimeMillis() + AUTO_FOLLOW_DELAY_MILLIS
        } else Long.MAX_VALUE
    }

    private fun updateMovementAwareTracking() {
        val location = map?.locationComponent ?: return
        if (!location.isLocationComponentActivated) return
        val tracking = location.cameraMode == CameraMode.TRACKING || location.cameraMode == CameraMode.TRACKING_GPS
        if (tracking) {
            followCurrentLocation()
        } else if (vehicleIsMoving()) {
            if (resumeFollowAtMillis == Long.MAX_VALUE) {
                resumeFollowAtMillis = android.os.SystemClock.uptimeMillis() + AUTO_FOLLOW_DELAY_MILLIS
            } else if (android.os.SystemClock.uptimeMillis() >= resumeFollowAtMillis) {
                followCurrentLocation()
            }
        }
    }

    private fun vehicleIsMoving(): Boolean =
        (map?.locationComponent?.lastKnownLocation?.speed ?: 0f) >= MOVING_SPEED_METERS_PER_SECOND

}

private const val AUTO_FOLLOW_DELAY_MILLIS = 6_000L
private const val MOVEMENT_CHECK_INTERVAL_MILLIS = 2_000L
private const val MOVING_SPEED_METERS_PER_SECOND = 1.4f

@Composable
fun MapLibreMapView(
    initialCenter: GeoPoint,
    onCenterChanged: (GeoPoint) -> Unit,
    controller: CaudalMapController,
    stores: List<MapStorePoint>,
    onStoreSelected: (String) -> Unit,
    onLocationPermissionGranted: () -> Unit = {},
    automaticFollow: Boolean = true,
    gpsIntervalSeconds: Int = 1,
    mapStyle: AppMapStyle = AppMapStyle.LIBERTY,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestCenterCallback by rememberUpdatedState(onCenterChanged)
    val latestPermissionCallback by rememberUpdatedState(onLocationPermissionGranted)
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var mapReady by remember { mutableStateOf<MapLibreMap?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(automaticFollow) {
        controller.setAutomaticFollow(automaticFollow)
    }
    LaunchedEffect(mapStyle) {
        controller.setMapStyle(mapStyle)
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) latestPermissionCallback()
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    val lifetime = remember(mapView) { MapViewLifetime() }

    DisposableEffect(lifecycle, mapView) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifetime.destroyed = true
            lifecycle.removeObserver(observer)
            controller.detach()
            runCatching { mapView.onPause() }
            runCatching { mapView.onStop() }
            runCatching { mapView.onDestroy() }
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    if (lifetime.destroyed) return@getMapAsync
                    controller.attach(map, context, latestCenterCallback)
                    mapReady = map
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(initialCenter.latitude, initialCenter.longitude))
                        .zoom(CAUDAL_NAVIGATION_ZOOM)
                        .tilt(if (mapStyle == AppMapStyle.THREE_D) 52.0 else 24.0)
                        .build()
                    map.setMinZoomPreference(9.0)
                    map.setMaxZoomPreference(19.0)
                    map.setMinPitchPreference(0.0)
                    map.setMaxPitchPreference(60.0)
                    map.setLatLngBoundsForCameraTarget(
                        LatLngBounds.Builder()
                            .include(LatLng(18.7, -92.5))
                            .include(LatLng(13.4, -88.0))
                            .build(),
                    )
                    // La brújula y el giro manual siguen disponibles como en una
                    // aplicación de navegación. La orientación automática no usa
                    // el magnetómetro: solo el rumbo GPS cuando hay movimiento.
                    map.uiSettings.isCompassEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isAttributionEnabled = true
                    map.setStyle(mapStyle.styleUrl) { style ->
                        if (lifetime.destroyed || controller.map !== map) return@setStyle
                        controller.onStyleLoaded(style)
                        if (permissionGranted) {
                            safelyEnableCaudalLocation(context, map, style, controller, gpsIntervalSeconds)
                        }
                    }
                }
            }
        },
        update = {
            controller.updateStores(stores, onStoreSelected, latestCenterCallback)
        },
        modifier = modifier,
    )

    LaunchedEffect(permissionGranted, mapReady) {
        val map = mapReady ?: return@LaunchedEffect
        if (permissionGranted && !lifetime.destroyed && controller.map === map) {
            map.style?.let {
                safelyEnableCaudalLocation(context, map, it, controller, gpsIntervalSeconds)
            }
        }
    }
}

private class MapViewLifetime(var destroyed: Boolean = false)

private fun StoreMarkerState.markerColor(): Int = when (this) {
    StoreMarkerState.DEBT -> Color.rgb(198, 40, 40)
    StoreMarkerState.PENDING_DELIVERY -> Color.rgb(239, 108, 0)
    StoreMarkerState.RECENT -> Color.rgb(46, 125, 50)
    StoreMarkerState.DUE_SOON -> Color.rgb(249, 168, 37)
    StoreMarkerState.INACTIVE -> Color.rgb(97, 97, 97)
}

private fun loadStoreMarkerBitmap(
    context: android.content.Context,
    state: StoreMarkerState,
): Bitmap = runCatching {
    context.assets.open("map/markers/${state.markerAssetName()}").use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream))
    }
}.getOrElse {
    createStoreMarkerBitmap(state.markerColor())
}

private fun StoreMarkerState.markerAssetName(): String = when (this) {
    StoreMarkerState.DEBT -> "store_debt.png"
    StoreMarkerState.PENDING_DELIVERY -> "store_pending_delivery.png"
    StoreMarkerState.RECENT -> "store_recent.png"
    StoreMarkerState.DUE_SOON -> "store_due_soon.png"
    StoreMarkerState.INACTIVE -> "store_inactive.png"
}

private fun storeCompositeImageId(store: MapStorePoint, badge: String?): String =
    "caudal-store-${store.id}-${listOf(store.name, store.state.name, badge).hashCode()}"

/**
 * Draws the label into the marker bitmap so store rendering never depends on
 * remote map glyphs. This also keeps custom marker assets working offline.
 */
private fun createStoreCompositeBitmap(
    context: android.content.Context,
    store: MapStorePoint,
    badge: String?,
): Bitmap {
    val width = 260
    val height = if (badge == null) 154 else 180
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(5f, 0f, 2f, Color.argb(70, 0, 0, 0))
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 50, 77)
        textSize = 27f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val displayName = ellipsizeForPaint(store.name, textPaint, width - 30f)
    canvas.drawRoundRect(8f, 5f, width - 8f, 48f, 18f, 18f, labelPaint)
    canvas.drawText(displayName, width / 2f, 35f, textPaint)

    val marker = loadStoreMarkerBitmap(context, store.state)
    val markerTop = if (badge == null) 53 else 76
    val markerLeft = (width - 84) / 2
    canvas.drawBitmap(marker, null, android.graphics.Rect(markerLeft, markerTop, markerLeft + 84, markerTop + 96), null)

    if (badge != null) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(183, 28, 28) }
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawRoundRect(44f, 52f, width - 44f, 82f, 15f, 15f, badgePaint)
        canvas.drawText(ellipsizeForPaint(badge, badgeText, width - 100f), width / 2f, 75f, badgeText)
    }
    return bitmap
}

private fun ellipsizeForPaint(value: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(value) <= maxWidth) return value
    var shortened = value
    while (shortened.isNotEmpty() && paint.measureText("$shortened…") > maxWidth) {
        shortened = shortened.dropLast(1)
    }
    return "$shortened…"
}

private fun createStoreMarkerBitmap(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(84, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }

    canvas.drawCircle(42f, 40f, 34f, fill)
    val pointer = Path().apply {
        moveTo(25f, 64f)
        lineTo(42f, 93f)
        lineTo(59f, 64f)
        close()
    }
    canvas.drawPath(pointer, fill)

    val roof = Path().apply {
        moveTo(20f, 40f)
        lineTo(42f, 21f)
        lineTo(64f, 40f)
        close()
    }
    canvas.drawPath(roof, white)
    canvas.drawRoundRect(26f, 38f, 58f, 63f, 4f, 4f, white)
    fill.color = color
    canvas.drawRoundRect(38f, 48f, 47f, 63f, 2f, 2f, fill)
    return bitmap
}

@SuppressLint("MissingPermission")
private fun enableCaudalLocation(
    context: android.content.Context,
    map: MapLibreMap,
    style: org.maplibre.android.maps.Style,
    intervalMillis: Long,
) {
    val location = map.locationComponent
    if (!location.isLocationComponentActivated) {
        val request = LocationEngineRequest.Builder(intervalMillis)
            .setFastestInterval(intervalMillis)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        location.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .locationEngineRequest(request)
                .build(),
        )
    }
    location.isLocationComponentEnabled = true
    location.renderMode = RenderMode.GPS
    location.setMaxAnimationFps(30)
}

private fun safelyEnableCaudalLocation(
    context: android.content.Context,
    map: MapLibreMap,
    style: org.maplibre.android.maps.Style,
    controller: CaudalMapController,
    gpsIntervalSeconds: Int,
) {
    try {
        enableCaudalLocation(context, map, style, gpsIntervalSeconds.coerceIn(1, 10) * 1_000L)
        controller.restoreCameraMode()
    } catch (error: Throwable) {
        // MapLibre executes this callback from JNI. Letting a Java exception escape here
        // aborts the complete Android process instead of producing a normal crash report.
        Log.e(CAUDAL_MAP_LOG_TAG, "No se pudo activar la capa de ubicación", error)
    }
}
