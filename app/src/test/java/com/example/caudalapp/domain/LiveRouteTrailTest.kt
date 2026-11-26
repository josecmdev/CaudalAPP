package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRouteTrailTest {
    private val start = GeoPoint(14.6349, -90.5069)
    private val about50mNorth = GeoPoint(14.63535, -90.5069)

    @Test
    fun `sin ruta no guarda puntos`() {
        val trail = LiveRouteTrail()
        assertFalse(trail.add(start, 0))
        assertTrue(trail.snapshot.isEmpty())
    }

    @Test
    fun `ignora puntos muy seguidos o muy cercanos`() {
        val trail = LiveRouteTrail().apply { reset(1L) }
        assertTrue(trail.add(start, 0))
        assertFalse(trail.add(about50mNorth, 5_000))
        assertFalse(trail.add(GeoPoint(14.63491, -90.5069), 20_000))
        assertTrue(trail.add(about50mNorth, 20_000))
        assertEquals(2, trail.snapshot.size)
    }

    @Test
    fun `al cambiar de ruta empieza de cero`() {
        val trail = LiveRouteTrail().apply { reset(1L); add(start, 0) }
        trail.reset(2L)
        assertTrue(trail.snapshot.isEmpty())
    }

    @Test
    fun `al llenarse reduce puntos y conserva el ultimo`() {
        val trail = LiveRouteTrail(minDistanceMeters = 0.0, minIntervalMillis = 0, maxPoints = 4).apply { reset(1L) }
        repeat(5) { trail.add(GeoPoint(14.0 + it, -90.0), it.toLong()) }
        assertEquals(GeoPoint(18.0, -90.0), trail.snapshot.last())
        assertTrue(trail.snapshot.size <= 4)
    }

    @Test
    fun `distancia aproximada en metros`() {
        assertEquals(50.0, LiveRouteTrail.distanceMeters(start, about50mNorth), 2.0)
    }
}
