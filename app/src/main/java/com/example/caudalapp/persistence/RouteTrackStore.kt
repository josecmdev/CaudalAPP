package com.example.caudalapp.persistence

import com.example.caudalapp.domain.RouteTrackPoint
import java.io.File

class RouteTrackStore(private val directory: File) {
    fun append(routeId: String, point: RouteTrackPoint) = synchronized(FILE_LOCK) {
        directory.mkdirs()
        routeFile(routeId).appendText(RouteTrackCodec.encode(point) + "\n", Charsets.UTF_8)
    }

    fun load(routeId: String): List<RouteTrackPoint> = synchronized(FILE_LOCK) {
        val file = routeFile(routeId)
        if (!file.exists()) return emptyList()
        file.useLines { lines -> lines.mapNotNull(RouteTrackCodec::decode).toList() }
    }

    fun count(routeId: String): Int = synchronized(FILE_LOCK) {
        val file = routeFile(routeId)
        if (!file.exists()) 0 else file.useLines { lines -> lines.count { RouteTrackCodec.decode(it) != null } }
    }

    fun delete(routeId: String): Boolean = synchronized(FILE_LOCK) {
        val file = routeFile(routeId)
        !file.exists() || file.delete()
    }

    private fun routeFile(routeId: String): File {
        require(routeId.matches(ROUTE_ID_PATTERN)) { "Identificador de recorrido no válido" }
        return File(directory, "route-$routeId.csv")
    }

    companion object {
        private val FILE_LOCK = Any()
        private val ROUTE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}

object RouteTrackCodec {
    fun encode(point: RouteTrackPoint): String = listOf(
        point.recordedAtEpochMillis.toString(),
        point.latitude.toString(),
        point.longitude.toString(),
        point.bearingDegrees?.toString().orEmpty(),
        point.accuracyMeters?.toString().orEmpty(),
    ).joinToString(",")

    fun decode(line: String): RouteTrackPoint? = runCatching {
        val values = line.split(',')
        if (values.size != 5) return null
        val point = RouteTrackPoint(
            recordedAtEpochMillis = values[0].toLong(),
            latitude = values[1].toDouble(),
            longitude = values[2].toDouble(),
            bearingDegrees = values[3].takeIf(String::isNotEmpty)?.toFloat(),
            accuracyMeters = values[4].takeIf(String::isNotEmpty)?.toFloat(),
        )
        if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) return null
        point
    }.getOrNull()
}
