package com.example.caudalapp.persistence

import com.example.caudalapp.domain.RouteTrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RouteTrackStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `agrega y recupera el recorrido completo en el mismo orden`() {
        val store = RouteTrackStore(temporaryFolder.newFolder("tracks"))
        val points = listOf(
            RouteTrackPoint(14.6349, -90.5069, 1_000L, 35f, 4.5f),
            RouteTrackPoint(14.6350, -90.5070, 2_000L, 37f, 4.2f),
            RouteTrackPoint(14.6351, -90.5071, 3_000L),
        )

        points.forEach { store.append("route-123", it) }

        assertEquals(points, store.load("route-123"))
        assertEquals(3, store.count("route-123"))
    }

    @Test
    fun `ignora lineas incompletas o coordenadas imposibles`() {
        val folder = temporaryFolder.newFolder("tracks-broken")
        val store = RouteTrackStore(folder)
        store.append("456", RouteTrackPoint(14.0, -90.0, 1L))
        java.io.File(folder, "route-456.csv").appendText("texto roto\n2,200,-90,,\n")

        assertEquals(1, store.load("456").size)
        assertNull(RouteTrackCodec.decode("2,200,-90,,"))
    }

    @Test
    fun `elimina el archivo de una ruta sin afectar los demas`() {
        val store = RouteTrackStore(temporaryFolder.newFolder("tracks-delete"))
        store.append("one", RouteTrackPoint(14.0, -90.0, 1L))
        store.append("two", RouteTrackPoint(15.0, -91.0, 2L))

        store.delete("one")

        assertFalse(store.load("two").isEmpty())
        assertEquals(emptyList<RouteTrackPoint>(), store.load("one"))
    }
}
