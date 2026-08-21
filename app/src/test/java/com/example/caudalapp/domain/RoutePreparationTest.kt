package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePreparationTest {

    @Test
    fun `permite iniciar una ruta personalizada sin sencillo ni inventario`() {
        val result = RoutePreparation.start(
            draft = RouteDraft(name = "Ruta especial", initialCash = 0),
            activeRouteExists = false,
        )

        assertTrue(result is RouteStartResult.Started)
        val route = (result as RouteStartResult.Started).route
        assertEquals("Ruta especial", route.name)
        assertEquals(0, route.ledger.cashOnHand)
    }

    @Test
    fun `un nombre vacio no bloquea el trabajo diario`() {
        val result = RoutePreparation.start(
            draft = RouteDraft(name = "   ", initialCash = 200),
            activeRouteExists = false,
        ) as RouteStartResult.Started

        assertEquals("Ruta sin nombre", result.route.name)
    }

    @Test
    fun `carga manualmente producto lleno y envases vacios`() {
        val result = RoutePreparation.start(
            draft = RouteDraft(
                name = "Viernes",
                initialCash = 300,
                stock = mapOf(
                    ProductCatalog.BAGS.id to 150,
                    ProductCatalog.WATER_JUG.id to 40,
                ),
                emptyContainers = mapOf(ProductCatalog.WATER_JUG.id to 12),
            ),
            activeRouteExists = false,
        ) as RouteStartResult.Started

        assertEquals(150, result.route.ledger.availableStock(ProductCatalog.BAGS.id))
        assertEquals(40, result.route.ledger.availableStock(ProductCatalog.WATER_JUG.id))
        assertEquals(12, result.route.ledger.emptyContainers(ProductCatalog.WATER_JUG.id))
    }

    @Test
    fun `rechaza cantidades negativas sin modificar ningun dato`() {
        val result = RoutePreparation.start(
            draft = RouteDraft(
                name = "Martes",
                initialCash = -1,
                stock = mapOf(ProductCatalog.BAGS.id to -5),
            ),
            activeRouteExists = false,
        )

        assertTrue(result is RouteStartResult.Rejected)
        val errors = (result as RouteStartResult.Rejected).errors
        assertTrue(errors.any { it.field == RouteField.INITIAL_CASH })
        assertTrue(errors.any { it.field == RouteField.STOCK })
    }

    @Test
    fun `impide iniciar una segunda ruta mientras otra sigue activa`() {
        val result = RoutePreparation.start(
            draft = RouteDraft(name = "Jueves", initialCash = 100),
            activeRouteExists = true,
        )

        assertFalse(result is RouteStartResult.Started)
        assertEquals(RouteField.ACTIVE_ROUTE, (result as RouteStartResult.Rejected).errors.single().field)
    }
}
