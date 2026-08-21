package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGpsPolicyTest {
    @Test
    fun `mantiene seguimiento rapido mientras hay movimiento reciente`() {
        assertEquals(1_000L, AdaptiveGpsPolicy.intervalMillis(1, 0L))
        assertEquals(3_000L, AdaptiveGpsPolicy.intervalMillis(3, 12_000L))
    }

    @Test
    fun `reduce la frecuencia cuando permanece detenido`() {
        assertEquals(10_000L, AdaptiveGpsPolicy.intervalMillis(1, 16_000L))
        assertEquals(10_000L, AdaptiveGpsPolicy.intervalMillis(5, 60_000L))
    }

    @Test
    fun `clasifica movimiento con umbral resistente al ruido del gps`() {
        assertFalse(AdaptiveGpsPolicy.isMoving(null))
        assertFalse(AdaptiveGpsPolicy.isMoving(0.8f))
        assertTrue(AdaptiveGpsPolicy.isMoving(1.5f))
    }
}
