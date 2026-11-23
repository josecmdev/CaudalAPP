package com.example.caudalapp.sync

import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.persistence.CaudalRestoredState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRowsTest {
    @Test
    fun `ruta activa genera filas de ruta y venta con nombre de tienda y llave por dispositivo`() {
        val bags = ProductCatalog.BAGS
        val ledger = RouteLedger.start(initialCash = 150, initialStock = mapOf(bags to 10))
        val stores = StoreDirectory { "s1" }
        val store = (stores.addStore("Tienda Azul", GeoPoint(14.6, -90.5)) as StoreChangeResult.Success).store
        ledger.registerStoreSale(store.id, listOf(SaleLine(bags, requested = 4, delivered = 4, total = 20)), amountReceived = 20)
        val state = CaudalRestoredState(ActiveRoute("Lunes", ledger, startedAtEpochMillis = 0L), stores, emptyList(), products = ProductDirectory())

        val route = SyncRows.groups(state, "tab-1").single { it.key == "route:0" }
        val tables = route.tables.toMap()

        val routeRow = tables.getValue("routes").single().jsonObject
        assertEquals("activa", routeRow.getValue("status").jsonPrimitive.content)
        assertEquals("1970-01-01T00:00:00.000Z", routeRow.getValue("started_at").jsonPrimitive.content)
        val line = tables.getValue("sale_lines").single().jsonObject
        assertEquals("tab-1", line.getValue("device_id").jsonPrimitive.content)
        assertEquals("Tienda Azul", line.getValue("store_name").jsonPrimitive.content)
        assertEquals("20", line.getValue("total").jsonPrimitive.content)
        assertEquals(ledger.auditEntries.size, tables.getValue("movements").size)
    }
}
