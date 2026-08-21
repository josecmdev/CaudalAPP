package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreDirectoryTest {

    private fun directory() = StoreDirectory(idGenerator = { "store-1" })

    @Test
    fun `agrega una tienda en la coordenada elegida por la mira`() {
        val directory = directory()

        val result = directory.addStore(
            name = "Tienda Lupita",
            location = GeoPoint(latitude = 14.6349, longitude = -90.5069),
            phone = "",
        )

        assertTrue(result is StoreChangeResult.Success)
        val store = (result as StoreChangeResult.Success).store
        assertEquals("Tienda Lupita", store.name)
        assertEquals(14.6349, store.location.latitude, 0.000001)
        assertEquals(null, store.phone)
    }

    @Test
    fun `rechaza nombres vacios y coordenadas fuera del planeta`() {
        val directory = directory()

        val result = directory.addStore(
            name = "   ",
            location = GeoPoint.unchecked(latitude = 91.0, longitude = -181.0),
        )

        assertTrue(result is StoreChangeResult.Rejected)
        assertEquals(2, (result as StoreChangeResult.Rejected).errors.size)
        assertTrue(directory.activeStores().isEmpty())
    }

    @Test
    fun `mover una tienda reemplaza su ubicacion sin crear una tienda nueva`() {
        val directory = directory()
        val store = (directory.addStore("La Esquina", GeoPoint(14.0, -90.0)) as StoreChangeResult.Success).store

        directory.moveStore(store.id, GeoPoint(14.1, -90.2))

        assertEquals(1, directory.activeStores().size)
        assertEquals(GeoPoint(14.1, -90.2), directory.get(store.id)?.location)
    }

    @Test
    fun `archivar oculta la tienda del mapa pero conserva sus datos`() {
        val directory = directory()
        val store = (directory.addStore("Temporal", GeoPoint(14.0, -90.0)) as StoreChangeResult.Success).store

        val result = directory.archiveStore(
            storeId = store.id,
            obligations = StoreObligations(moneyDue = 50, containersDue = 2, unitsPending = 0),
            confirmed = true,
        )

        assertTrue(result.archived)
        assertTrue(result.hadPendingObligations)
        assertTrue(directory.activeStores().isEmpty())
        assertTrue(directory.get(store.id)?.archived == true)
    }

    @Test
    fun `archivar con pendientes exige confirmar la advertencia`() {
        val directory = directory()
        val store = (directory.addStore("Temporal", GeoPoint(14.0, -90.0)) as StoreChangeResult.Success).store

        val result = directory.archiveStore(
            storeId = store.id,
            obligations = StoreObligations(moneyDue = 8),
            confirmed = false,
        )

        assertFalse(result.archived)
        assertFalse(directory.get(store.id)?.archived == true)
    }

    @Test
    fun `edita nombre y telefono conservando identidad y ubicacion`() {
        val directory = StoreDirectory { "store-edit" }
        val original = (directory.addStore("Tienda", GeoPoint(14.6, -90.5)) as StoreChangeResult.Success).store

        val result = directory.updateStore(original.id, "Tienda Reforma", "5555-0000")

        val edited = (result as StoreChangeResult.Success).store
        assertEquals("store-edit", edited.id)
        assertEquals("Tienda Reforma", edited.name)
        assertEquals("5555-0000", edited.phone)
        assertEquals(GeoPoint(14.6, -90.5), edited.location)
    }

    @Test
    fun `restaura una tienda archivada`() {
        val directory = StoreDirectory { "store-restore" }
        val store = (directory.addStore("Temporal", GeoPoint(14.6, -90.5)) as StoreChangeResult.Success).store
        directory.archiveStore(store.id, confirmed = true)

        assertTrue(directory.restoreStore(store.id))
        assertFalse(directory.get(store.id)!!.archived)
        assertEquals(1, directory.activeStores().size)
    }
}
