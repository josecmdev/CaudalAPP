package com.example.caudalapp

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.caudalapp.domain.RouteTrackPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.FeatureCollection

private const val HISTORY_MAP_STYLE = "https://tiles.openfreemap.org/styles/bright"
private const val TRACK_SOURCE_ID = "caudal-history-track-source"
private const val TRACK_LAYER_ID = "caudal-history-track-layer"
private const val SALES_SOURCE_ID = "caudal-history-sales-source"
private const val SALES_LAYER_ID = "caudal-history-sales-layer"

data class MapSalePoint(val location: com.example.caudalapp.domain.GeoPoint, val quickSale: Boolean)

private class RouteTrackMapController {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var points: List<RouteTrackPoint> = emptyList()
    private var sales: List<MapSalePoint> = emptyList()
    private var renderedSignature: Triple<Long, Int, Int>? = null

    fun attach(map: MapLibreMap, style: Style) {
        this.map = map
        this.style = style
        render()
    }

    fun detach() {
        map = null
        style = null
        renderedSignature = null
    }

    fun update(points: List<RouteTrackPoint>) {
        this.points = points
        render()
    }

    fun updateSales(sales: List<MapSalePoint>) {
        this.sales = sales
        render()
    }

    private fun render() {
        val currentMap = map ?: return
        val currentStyle = style ?: return
        if (points.isEmpty() && sales.isEmpty()) return
        val signature = Triple(points.firstOrNull()?.recordedAtEpochMillis ?: 0L, points.size, sales.size)
        if (signature == renderedSignature) return
        currentStyle.getLayer(TRACK_LAYER_ID)?.let { currentStyle.removeLayer(it) }
        currentStyle.getSource(TRACK_SOURCE_ID)?.let { currentStyle.removeSource(it) }
        currentStyle.getLayer(SALES_LAYER_ID)?.let { currentStyle.removeLayer(it) }
        currentStyle.getSource(SALES_SOURCE_ID)?.let { currentStyle.removeSource(it) }
        if (points.size >= 2) {
            val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
            currentStyle.addSource(GeoJsonSource(TRACK_SOURCE_ID, Feature.fromGeometry(line)))
            currentStyle.addLayer(
                LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                    lineColor("#0878E8"),
                    lineWidth(5f),
                    lineOpacity(.88f),
                ),
            )
        }
        if (sales.isNotEmpty()) {
            val features = sales.map { sale ->
                Feature.fromGeometry(Point.fromLngLat(sale.location.longitude, sale.location.latitude))
            }
            currentStyle.addSource(GeoJsonSource(SALES_SOURCE_ID, FeatureCollection.fromFeatures(features)))
            currentStyle.addLayer(
                CircleLayer(SALES_LAYER_ID, SALES_SOURCE_ID).withProperties(
                    circleColor("#6C3CEB"),
                    circleRadius(8f),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(3f),
                ),
            )
        }
        val boundsBuilder = LatLngBounds.Builder()
        val allLocations = points.map { it.latitude to it.longitude } +
            sales.map { it.location.latitude to it.location.longitude }
        allLocations.forEach { (latitude, longitude) -> boundsBuilder.include(LatLng(latitude, longitude)) }
        val hasDifferentLocations = allLocations.asSequence()
            .distinct()
            .take(2)
            .count() > 1
        if (!hasDifferentLocations) {
            val firstLocation = allLocations.first()
            currentMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(firstLocation.first, firstLocation.second),
                    17.0,
                ),
            )
        } else {
            currentMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
        }
        renderedSignature = signature
    }
}

@Composable
fun RouteTrackMapView(
    points: List<RouteTrackPoint>,
    sales: List<MapSalePoint> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { RouteTrackMapController() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    val lifetime = remember(mapView) { HistoryMapLifetime() }

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
                    map.uiSettings.isCompassEnabled = true
                    map.setStyle(HISTORY_MAP_STYLE) { style ->
                        if (!lifetime.destroyed) controller.attach(map, style)
                    }
                }
            }
        },
        update = {
            controller.update(points)
            controller.updateSales(sales)
        },
        modifier = modifier,
    )
}

private class HistoryMapLifetime(var destroyed: Boolean = false)
