package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAreaPolicyTest {
    @Test
    fun `crea limites alrededor del centro para descarga local`() {
        val bounds = OfflineAreaPolicy.boundsAround(GeoPoint(14.6349, -90.5069), 15)

        assertTrue(bounds.southWest.latitude < 14.6349)
        assertTrue(bounds.southWest.longitude < -90.5069)
        assertTrue(bounds.northEast.latitude > 14.6349)
        assertTrue(bounds.northEast.longitude > -90.5069)
        assertEquals(14.6349, (bounds.southWest.latitude + bounds.northEast.latitude) / 2, 0.000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza radios demasiado grandes`() {
        OfflineAreaPolicy.boundsAround(GeoPoint(14.6, -90.5), 100)
    }
}
