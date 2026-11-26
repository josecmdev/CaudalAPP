package com.example.caudalapp.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SyncSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val userId: String,
    val email: String,
)

/** Cliente mínimo de Supabase (Auth + REST) con HttpURLConnection para no sumar dependencias. */
class SupabaseApi(private val baseUrl: String, private val anonKey: String) {

    fun signIn(email: String, password: String): SyncSession = authToken(
        "password",
        buildJsonObject {
            put("email", email)
            put("password", password)
        },
    )

    fun refresh(session: SyncSession): SyncSession =
        authToken("refresh_token", buildJsonObject { put("refresh_token", session.refreshToken) })

    fun upsert(session: SyncSession, table: String, rows: JsonArray) {
        if (rows.isEmpty()) return
        request(
            "POST",
            "/rest/v1/$table",
            rows.toString(),
            session.accessToken,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    fun insert(session: SyncSession, table: String, row: JsonObject) {
        request("POST", "/rest/v1/$table", row.toString(), session.accessToken, mapOf("Prefer" to "return=minimal"))
    }

    fun get(session: SyncSession, path: String): String = request("GET", path, null, session.accessToken)

    private fun authToken(grantType: String, body: JsonObject): SyncSession {
        val response = Json.parseToJsonElement(request("POST", "/auth/v1/token?grant_type=$grantType", body.toString(), null)).jsonObject
        val user = response.getValue("user").jsonObject
        return SyncSession(
            accessToken = response.getValue("access_token").jsonPrimitive.content,
            refreshToken = response.getValue("refresh_token").jsonPrimitive.content,
            expiresAtEpochMillis = response.getValue("expires_at").jsonPrimitive.long * 1000L,
            userId = user.getValue("id").jsonPrimitive.content,
            email = user["email"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        accessToken: String?,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = body != null
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: anonKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            headers.forEach(connection::setRequestProperty)
            body?.let { text -> connection.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw SupabaseException(code, text)
            return text
        } finally {
            connection.disconnect()
        }
    }
}

class SupabaseException(val code: Int, body: String) : IOException("Supabase respondió $code: ${body.take(300)}")
