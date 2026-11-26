package com.example.caudalapp.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.caudalapp.BuildConfig
import com.example.caudalapp.DataBackup
import com.example.caudalapp.RouteTrackingService
import com.example.caudalapp.persistence.CaudalStateStore
import com.example.caudalapp.persistence.CaudalStateWriter
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.Store
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sincronización opcional y en segundo plano. La app nunca depende de esto: sin llaves de Supabase,
 * sin sesión o sin internet todo sigue local y se reintenta cada minuto.
 */
object CaudalSync {
    private const val PREFERENCES = "caudal-sync-v1"
    private const val INTERVAL_SECONDS = 60L

    val configured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    var signedInEmail by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf("")
        private set

    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "caudal-sync").apply { isDaemon = true }
    }
    private var started = false
    private var startedContext: Context? = null
    private val api by lazy { SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }

    fun start(context: Context) {
        if (started || !configured) return
        started = true
        val appContext = context.applicationContext
        startedContext = appContext
        signedInEmail = loadSession(appContext)?.email
        executor.scheduleWithFixedDelay({ runCatching { syncOnce(appContext) } }, 5, INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    /** Usuario sin @ se toma como `usuario@caudal.local`, así el admin puede crear cuentas sin correo real. */
    fun signIn(context: Context, user: String, password: String): Result<Unit> = runCatching {
        // El teclado de la tablet suele poner mayúscula inicial; los correos de Supabase van en minúsculas.
        val email = user.trim().lowercase().let { if ('@' in it) it else "$it@caudal.local" }
        val session = try {
            executor.submit<SyncSession> { api.signIn(email, password) }.get()
        } catch (wrapped: java.util.concurrent.ExecutionException) {
            throw wrapped.cause ?: wrapped
        }
        val preferences = prefs(context)
        val previousUser = preferences.getString("user_id", null)
        preferences.edit().apply {
            // Otro usuario en la misma tablet sube todo de nuevo bajo su propio dispositivo.
            if (previousUser != session.userId) preferences.all.keys.filter { it.startsWith("hash:") }.forEach(::remove).also { remove("stores_pulled_at") }
        }.apply()
        saveSession(context, session)
        signedInEmail = session.email
        pullSharedStores(context)
    }

    fun loginErrorMessage(error: Throwable): String = when {
        error is SupabaseException && "email_not_confirmed" in (error.message ?: "") ->
            "La cuenta no está confirmada. En Supabase → Authentication → Users, ábrela y confírmala."
        error is SupabaseException && "invalid_credentials" in (error.message ?: "") ->
            "Usuario o contraseña incorrectos. Escribe el correo completo tal como está en Supabase."
        error is SupabaseException -> "Supabase rechazó el inicio de sesión: ${error.message}"
        else -> "Sin conexión con Supabase. Revisa el internet de la tablet."
    }

    fun signOut(context: Context) {
        prefs(context).edit().remove("access_token").remove("refresh_token").apply()
        signedInEmail = null
        status = ""
    }

    fun syncNow(context: Context) {
        val appContext = context.applicationContext
        executor.execute { runCatching { syncOnce(appContext) } }
    }

    /** Lo registra la pantalla principal para recibir tiendas nuevas o cambiadas de otras tabletas. */
    @Volatile
    var sharedStoresListener: ((List<Store>) -> Unit)? = null

    /** Al iniciar sesión o ruta: primero sube lo local y luego baja solo las tiendas que cambiaron desde la última vez. */
    fun pullSharedStores(context: Context? = null) {
        val appContext = context?.applicationContext ?: startedContext ?: return
        executor.execute {
            runCatching {
                syncOnce(appContext)
                pullStores(appContext)
            }.onFailure { status = "Sin bajar tiendas: ${it.message ?: "sin conexión"}" }
        }
    }

    private fun pullStores(context: Context) {
        val session = loadSession(context) ?: return
        val listener = sharedStoresListener ?: return
        val preferences = prefs(context)
        val since = preferences.getString("stores_pulled_at", null) ?: "1970-01-01T00:00:00Z"
        val pulled = mutableListOf<Store>()
        var newest = since
        while (true) {
            val page = Json.parseToJsonElement(
                api.get(
                    session,
                    "/rest/v1/shared_stores?select=id,name,latitude,longitude,phone,archived,updated_at" +
                        "&updated_at=gt.${URLEncoder.encode(since, "UTF-8")}&order=updated_at&limit=1000&offset=${pulled.size}",
                ),
            ).jsonArray
            page.forEach { element ->
                val row = element.jsonObject
                pulled += Store(
                    id = row.getValue("id").jsonPrimitive.content,
                    name = row.getValue("name").jsonPrimitive.content,
                    location = GeoPoint(row.getValue("latitude").jsonPrimitive.double, row.getValue("longitude").jsonPrimitive.double),
                    phone = row["phone"]?.jsonPrimitive?.contentOrNull,
                    archived = row.getValue("archived").jsonPrimitive.boolean,
                )
                newest = row.getValue("updated_at").jsonPrimitive.content
            }
            if (page.size < 1000) break
        }
        if (pulled.isEmpty()) return
        // Marcarlas como ya subidas para que la mezcla no las devuelva a Supabase.
        preferences.edit().apply {
            pulled.forEach { putString("hash:store:${it.id}", SyncRows.hash(SyncRows.storeGroup(it))) }
            putString("stores_pulled_at", newest)
        }.apply()
        android.os.Handler(android.os.Looper.getMainLooper()).post { listener(pulled) }
    }

    private fun syncOnce(context: Context) {
        var session = loadSession(context) ?: return
        try {
            if (System.currentTimeMillis() > session.expiresAtEpochMillis - 120_000L) {
                session = try {
                    api.refresh(session)
                } catch (expired: SupabaseException) {
                    // Sesión vencida o usuario borrado por el admin: pedir login de nuevo.
                    if (expired.code in 400..401) signOut(context)
                    throw expired
                }
                saveSession(context, session)
            }
            val deviceId = deviceId(context, session.userId)
            val location = newestLocation(context)
            val preferences = prefs(context)
            api.upsert(session, "devices", JsonArray(listOf(buildJsonObject {
                put("id", deviceId)
                put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("last_seen_at", SyncRows.iso(System.currentTimeMillis()))
                location?.let {
                    put("last_latitude", it.point.latitude)
                    put("last_longitude", it.point.longitude)
                    put("last_position_at", SyncRows.iso(it.recordedAtEpochMillis))
                }
            })))
            if (location != null && location.recordedAtEpochMillis > preferences.getLong("last_position_sent", 0L)) {
                api.insert(session, "positions", buildJsonObject {
                    put("device_id", deviceId)
                    put("route_id", location.routeId)
                    put("latitude", location.point.latitude)
                    put("longitude", location.point.longitude)
                    put("recorded_at", SyncRows.iso(location.recordedAtEpochMillis))
                })
                preferences.edit().putLong("last_position_sent", location.recordedAtEpochMillis).apply()
            }
            val state = CaudalStateWriter.runBlocking {
                CaudalStateStore(File(context.filesDir, DataBackup.STATE_FILE)).load()
            }
            val changed = state?.let { SyncRows.groups(it, deviceId) }.orEmpty()
                .map { it to SyncRows.hash(it) }
                .filter { (group, hash) -> preferences.getString("hash:${group.key}", null) != hash }
            // Un solo envío por tabla con todo lo que cambió, para gastar pocas peticiones.
            changed.flatMap { (group, _) -> group.tables }
                .groupBy({ it.first }, { it.second })
                .forEach { (table, arrays) -> api.upsert(session, table, JsonArray(arrays.flatten())) }
            preferences.edit().apply { changed.forEach { (group, hash) -> putString("hash:${group.key}", hash) } }.apply()
            status = "Sincronizado ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}" +
                if (changed.isNotEmpty()) " (${changed.size} cambios)" else ""
        } catch (error: Exception) {
            status = "Sin sincronizar: ${error.message ?: "sin conexión"}"
        }
    }

    /**
     * La ruta guarda solo puntos precisos para el recorrido; para la oficina basta la última posición que Android
     * ya tenga (también fuera de ruta). getLastKnownLocation no enciende el GPS, así que no gasta batería.
     */
    @android.annotation.SuppressLint("MissingPermission") // el permiso se revisa arriba en `granted`
    private fun newestLocation(context: Context): RouteTrackingService.Companion.KnownLocation? {
        val tracked = RouteTrackingService.latestKnownLocation(context)
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return tracked
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val system = runCatching {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull(Location::getTime)
        }.getOrNull()
            ?.let { RouteTrackingService.Companion.KnownLocation(tracked?.routeId, GeoPoint(it.latitude, it.longitude), it.time) }
            ?.takeIf { it.point.isValid }
        return listOfNotNull(tracked, system).maxByOrNull { it.recordedAtEpochMillis }
    }

    private fun deviceId(context: Context, userId: String): String {
        val preferences = prefs(context)
        val install = preferences.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("install_id", it).apply()
        }
        return "$install-${userId.take(8)}"
    }

    private fun saveSession(context: Context, session: SyncSession) {
        prefs(context).edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putLong("expires_at", session.expiresAtEpochMillis)
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .apply()
    }

    private fun loadSession(context: Context): SyncSession? {
        val preferences = prefs(context)
        return SyncSession(
            accessToken = preferences.getString("access_token", null) ?: return null,
            refreshToken = preferences.getString("refresh_token", null) ?: return null,
            expiresAtEpochMillis = preferences.getLong("expires_at", 0L),
            userId = preferences.getString("user_id", null) ?: return null,
            email = preferences.getString("email", "").orEmpty(),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)


}
