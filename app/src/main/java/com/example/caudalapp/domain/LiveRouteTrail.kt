package com.example.caudalapp.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Línea del recorrido que se ve en el mapa durante la ruta. Filtra puntos para que el mapa no redibuje de más. */
class LiveRouteTrail(
    private val minDistanceMeters: Double = 15.0,
    private val minIntervalMillis: Long = 10_000L,
    private val maxPoints: Int = 2_000,
) {
    var key: Long? = null
        private set
    private val points = mutableListOf<GeoPoint>()
    private var lastAddedAtMillis = 0L

    val snapshot: List<GeoPoint> get() = points.toList()

    fun reset(newKey: Long?) {
        key = newKey
        points.clear()
        lastAddedAtMillis = 0L
    }

    /** Devuelve true si el punto se agregó y hay que redibujar. */
    fun add(point: GeoPoint, nowMillis: Long): Boolean {
        if (key == null) return false
        val last = points.lastOrNull()
        if (last != null) {
            if (nowMillis - lastAddedAtMillis < minIntervalMillis) return false
            if (distanceMeters(last, point) < minDistanceMeters) return false
        }
        points += point
        lastAddedAtMillis = nowMillis
        // Al llenarse se queda con uno de cada dos: la forma del camino se mantiene y el dibujo no crece sin fin.
        if (points.size > maxPoints) {
            val kept = points.filterIndexed { index, _ -> index % 2 == 0 || index == points.lastIndex }
            points.clear()
            points += kept
        }
        return true
    }

    companion object {
        fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
            return 2 * 6_371_000.0 * asin(sqrt(h))
        }
    }
}
