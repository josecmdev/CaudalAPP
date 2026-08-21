package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteElapsedPolicyTest {
    @Test
    fun `suma el tramo activo al tiempo acumulado`() {
        assertEquals(25_000L, RouteElapsedPolicy.currentElapsedMillis(10_000L, 5_000L, 20_000L))
    }

    @Test
    fun `no suma el tiempo apagado cuando el reloj monotono reinicio`() {
        assertEquals(10_000L, RouteElapsedPolicy.currentElapsedMillis(10_000L, 500_000L, 2_000L))
    }

    @Test
    fun `un reloj congelado solo devuelve lo acumulado`() {
        assertEquals(18_000L, RouteElapsedPolicy.currentElapsedMillis(18_000L, -1L, 99_000L))
    }
}
