package com.example.caudalapp.domain

data class RouteTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMillis: Long,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
)
