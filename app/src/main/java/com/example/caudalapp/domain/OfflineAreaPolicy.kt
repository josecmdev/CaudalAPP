package com.example.caudalapp.domain

import kotlin.math.cos

data class OfflineAreaBounds(val southWest: GeoPoint, val northEast: GeoPoint)

object OfflineAreaPolicy {
    fun boundsAround(center: GeoPoint, radiusKm: Int): OfflineAreaBounds {
        require(center.isValid)
        require(radiusKm in 1..50)
        val latitudeDelta = radiusKm / 111.32
        val longitudeScale = cos(Math.toRadians(center.latitude)).coerceAtLeast(0.1)
        val longitudeDelta = radiusKm / (111.32 * longitudeScale)
        return OfflineAreaBounds(
            southWest = GeoPoint(center.latitude - latitudeDelta, center.longitude - longitudeDelta),
            northEast = GeoPoint(center.latitude + latitudeDelta, center.longitude + longitudeDelta),
        )
    }
}
