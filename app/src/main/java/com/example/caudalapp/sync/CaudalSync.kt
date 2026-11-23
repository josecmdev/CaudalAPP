package com.example.caudalapp.sync

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.caudalapp.BuildConfig
import com.example.caudalapp.DataBackup
import com.example.caudalapp.RouteTrackingService
import com.example.caudalapp.persistence.CaudalStateStore
import com.example.caudalapp.persistence.CaudalStateWriter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
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
    private val api by lazy { SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }

    fun start(context: Context) {
        if (started || !configured) return
        started = true
        val appContext = context.applicationContext
        signedInEmail = loadSession(appContext)?.email
        executor.scheduleWithFixedDelay({ runCatching { syncOnce(appContext) } }, 5, INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    /** Usuario sin @ se toma como `usuario@caudal.local`, así el admin puede crear cuentas sin correo real. */
    fun signIn(context: Context, user: String, password: String): Result<Unit> = runCatching {
        val email = user.trim().let { if ('@' in it) it else "$it@caudal.local" }
        val session = executor.submit<SyncSession> { api.signIn(email, password) }.get()
        val preferences = prefs(context)
        val previousUser = preferences.getString("user_id", null)
        preferences.edit().apply {
            // Otro usuario en la misma tablet sube todo de nuevo bajo su propio dispositivo.
            if (previousUser != session.userId) preferences.all.keys.filter { it.startsWith("hash:") }.forEach(::remove)
        }.apply()
        saveSession(context, session)
        signedInEmail = session.email
        syncNow(context)
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
            val location = RouteTrackingService.latestKnownLocation(context)
            val preferences = prefs(context)
            api.upsert(session, "devices", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
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
            var uploaded = 0
            state?.let { SyncRows.groups(it, deviceId) }?.forEach { group ->
                val hash = sha1(group.tables.joinToString("\n") { (table, rows) -> "$table=$rows" })
                if (preferences.getString("hash:${group.key}", null) == hash) return@forEach
                group.tables.forEach { (table, rows) -> api.upsert(session, table, rows) }
                preferences.edit().putString("hash:${group.key}", hash).apply()
                uploaded++
            }
            status = "Sincronizado ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}" +
                if (uploaded > 0) " ($uploaded cambios)" else ""
        } catch (error: Exception) {
            status = "Sin sincronizar: ${error.message ?: "sin conexión"}"
        }
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

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
