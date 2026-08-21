package com.example.caudalapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.caudalapp.domain.RouteTrackPoint
import com.example.caudalapp.domain.RouteElapsedPolicy
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.AdaptiveGpsPolicy
import com.example.caudalapp.persistence.RouteTrackStore
import java.io.File

class RouteTrackingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var workerThread: HandlerThread
    private lateinit var trackStore: RouteTrackStore
    private var activeRouteId: String? = null
    private var lastSavedAtElapsedMillis = 0L
    private var locationIntervalMillis = 1_000L
    private var configuredMovingIntervalSeconds = 1
    private var lastMovementAtElapsedMillis = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(LocationManager::class.java)
        workerThread = HandlerThread("caudal-route-gps").apply { start() }
        trackStore = RouteTrackStore(File(filesDir, TRACK_DIRECTORY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routeId = intent?.getStringExtra(EXTRA_ROUTE_ID)
            ?: getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_ACTIVE_ROUTE_ID, null)
        if (routeId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, trackingNotification())
        activeRouteId = routeId
        configuredMovingIntervalSeconds = (intent?.getIntExtra(EXTRA_INTERVAL_SECONDS, -1)
            ?.takeIf { it > 0 }
            ?: getSharedPreferences(PREFERENCES, MODE_PRIVATE).getInt(KEY_INTERVAL_SECONDS, 1))
            .coerceIn(1, 10)
        locationIntervalMillis = configuredMovingIntervalSeconds * 1_000L
        lastMovementAtElapsedMillis = SystemClock.elapsedRealtime()
        ensureElapsedClock(this, routeId)
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_ROUTE_ID, routeId)
            .putInt(KEY_INTERVAL_SECONDS, configuredMovingIntervalSeconds)
            .apply()
        beginLocationUpdates()
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun beginLocationUpdates() {
        runCatching { locationManager.removeUpdates(this) }
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            stopSelf()
            return
        }

        val providers = buildList {
            if (fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    locationIntervalMillis,
                    0f,
                    this,
                    workerThread.looper,
                )
            }
        }
        if (providers.isEmpty()) stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (AdaptiveGpsPolicy.isMoving(location.speed.takeIf { location.hasSpeed() })) {
            lastMovementAtElapsedMillis = nowElapsed
        }
        val desiredInterval = AdaptiveGpsPolicy.intervalMillis(
            configuredMovingSeconds = configuredMovingIntervalSeconds,
            millisecondsSinceMovement = nowElapsed - lastMovementAtElapsedMillis,
        )
        if (desiredInterval != locationIntervalMillis) {
            locationIntervalMillis = desiredInterval
            beginLocationUpdates()
        }
        if (lastSavedAtElapsedMillis != 0L &&
            nowElapsed - lastSavedAtElapsedMillis < (locationIntervalMillis * 9L / 10L)
        ) return
        val routeId = activeRouteId ?: return
        runCatching {
            trackStore.append(
                routeId,
                RouteTrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    recordedAtEpochMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                    accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                ),
            )
        }.onSuccess {
            lastSavedAtElapsedMillis = nowElapsed
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString(KEY_LATEST_LOCATION_ROUTE_ID, routeId)
                .putLong(KEY_LATEST_LATITUDE, location.latitude.toBits())
                .putLong(KEY_LATEST_LONGITUDE, location.longitude.toBits())
                .apply()
        }
    }

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        workerThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Seguimiento de ruta",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene el recorrido GPS mientras la pantalla está apagada"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun trackingNotification(): Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Caudal App · ruta activa")
        .setContentText("Guardando el recorrido GPS")
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        private const val EXTRA_ROUTE_ID = "route-id"
        private const val EXTRA_INTERVAL_SECONDS = "interval-seconds"
        private const val PREFERENCES = "caudal-route-tracking"
        private const val KEY_ACTIVE_ROUTE_ID = "active-route-id"
        private const val TRACK_DIRECTORY = "route-tracks"
        private const val NOTIFICATION_CHANNEL_ID = "caudal-active-route"
        private const val NOTIFICATION_ID = 71
        private const val KEY_INTERVAL_SECONDS = "interval-seconds"
        private const val CLOCK_ROUTE_ID = "clock-route-id"
        private const val CLOCK_ACCUMULATED = "clock-accumulated"
        private const val CLOCK_STARTED = "clock-started"
        private const val KEY_LATEST_LOCATION_ROUTE_ID = "latest-location-route-id"
        private const val KEY_LATEST_LATITUDE = "latest-latitude"
        private const val KEY_LATEST_LONGITUDE = "latest-longitude"

        fun routeId(startedAtEpochMillis: Long): String = startedAtEpochMillis.toString()

        fun start(context: Context, routeId: String, intervalSeconds: Int = 1) {
            ensureElapsedClock(context, routeId)
            val intent = Intent(context, RouteTrackingService::class.java)
                .putExtra(EXTRA_ROUTE_ID, routeId)
                .putExtra(EXTRA_INTERVAL_SECONDS, intervalSeconds.coerceIn(1, 10))
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            freezeElapsedClock(context)
            context.stopService(Intent(context, RouteTrackingService::class.java))
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(KEY_ACTIVE_ROUTE_ID).apply()
        }

        fun trackStore(context: Context): RouteTrackStore =
            RouteTrackStore(File(context.filesDir, TRACK_DIRECTORY))

        fun latestLocation(context: Context, routeId: String): GeoPoint? {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (preferences.getString(KEY_LATEST_LOCATION_ROUTE_ID, null) != routeId) return null
            if (!preferences.contains(KEY_LATEST_LATITUDE) || !preferences.contains(KEY_LATEST_LONGITUDE)) return null
            return GeoPoint(
                Double.fromBits(preferences.getLong(KEY_LATEST_LATITUDE, 0L)),
                Double.fromBits(preferences.getLong(KEY_LATEST_LONGITUDE, 0L)),
            ).takeIf(GeoPoint::isValid)
        }

        @Synchronized
        fun ensureElapsedClock(context: Context, routeId: String) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val now = SystemClock.elapsedRealtime()
            val savedRouteId = preferences.getString(CLOCK_ROUTE_ID, null)
            val savedStarted = preferences.getLong(CLOCK_STARTED, -1L)
            when {
                savedRouteId != routeId -> preferences.edit()
                    .putString(CLOCK_ROUTE_ID, routeId)
                    .putLong(CLOCK_ACCUMULATED, 0L)
                    .putLong(CLOCK_STARTED, now)
                    .apply()
                savedStarted < 0L || savedStarted > now -> preferences.edit()
                    .putLong(CLOCK_STARTED, now)
                    .apply()
            }
        }

        @Synchronized
        fun elapsedMillis(context: Context, routeId: String): Long {
            ensureElapsedClock(context, routeId)
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return RouteElapsedPolicy.currentElapsedMillis(
                preferences.getLong(CLOCK_ACCUMULATED, 0L),
                preferences.getLong(CLOCK_STARTED, -1L),
                SystemClock.elapsedRealtime(),
            )
        }

        @Synchronized
        private fun freezeElapsedClock(context: Context): Long {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val routeId = preferences.getString(CLOCK_ROUTE_ID, null) ?: return 0L
            val elapsed = elapsedMillis(context, routeId)
            preferences.edit()
                .putLong(CLOCK_ACCUMULATED, elapsed)
                .putLong(CLOCK_STARTED, -1L)
                .apply()
            return elapsed
        }
    }
}
