package com.example.caudalapp.sync

import com.example.caudalapp.domain.RouteAuditEntry
import com.example.caudalapp.domain.SaleRecord
import com.example.caudalapp.persistence.CaudalRestoredState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Filas para Supabase agrupadas: un grupo solo se vuelve a subir si su contenido cambió. */
data class SyncGroup(val key: String, val tables: List<Pair<String, JsonArray>>)

object SyncRows {
    fun groups(state: CaudalRestoredState, deviceId: String): List<SyncGroup> {
        val storeNames = state.stores.allStores().associate { it.id to it.name }
        val groups = mutableListOf<SyncGroup>()
        groups += SyncGroup("products", listOf("products" to buildJsonArray {
            state.products.allProducts().forEach { product ->
                add(buildJsonObject {
                    put("device_id", deviceId)
                    put("id", product.id)
                    put("name", product.name)
                    put("suggested_price", product.defaultSuggestedTotal)
                    put("returnable", product.returnable)
                    put("archived", product.archived)
                })
            }
        }))
        groups += SyncGroup("stores", listOf("stores" to buildJsonArray {
            state.stores.allStores().forEach { store ->
                add(buildJsonObject {
                    put("device_id", deviceId)
                    put("id", store.id)
                    put("name", store.name)
                    put("latitude", store.location.latitude)
                    put("longitude", store.location.longitude)
                    put("phone", store.phone)
                    put("archived", store.archived)
                })
            }
        }))
        state.completedRoutes.forEach { record ->
            val routeId = record.startedAtEpochMillis.toString()
            val summary = record.summary
            val route = buildJsonObject {
                put("device_id", deviceId)
                put("id", routeId)
                put("name", record.routeName)
                put("status", "finalizada")
                put("started_at", iso(record.startedAtEpochMillis))
                put("finished_at", iso(record.finishedAtEpochMillis))
                put("initial_cash", summary.initialCash)
                put("total_sales", summary.totalSales)
                put("cash_collected", summary.cashCollected)
                put("money_pending", summary.moneyPending)
                put("total_expenses", summary.totalExpenses)
                put("expected_cash", summary.expectedCash)
                put("counted_cash", record.countedCash)
                putJsonObject("stock") { record.expectedStock.forEach { (id, units) -> put(id, units) } }
                putJsonObject("counted_stock") { record.countedStock.forEach { (id, units) -> put(id, units) } }
                put("archived", record.archived)
            }
            groups += routeGroup(deviceId, routeId, route, record.sales, record.auditEntries, storeNames)
        }
        state.activeRoute?.let { active ->
            val ledger = active.ledger.exportState()
            val routeId = active.startedAtEpochMillis.toString()
            val route = buildJsonObject {
                put("device_id", deviceId)
                put("id", routeId)
                put("name", active.name)
                put("status", "activa")
                put("started_at", iso(active.startedAtEpochMillis))
                put("initial_cash", ledger.initialCash)
                put("total_sales", ledger.totalSales)
                put("cash_collected", ledger.cashCollected)
                put("total_expenses", ledger.totalExpenses)
                put("expected_cash", ledger.cashOnHand)
                putJsonObject("stock") { ledger.stock.forEach { (id, units) -> put(id, units) } }
            }
            groups += routeGroup(deviceId, routeId, route, ledger.sales, ledger.auditEntries, storeNames)
        }
        return groups
    }

    private fun routeGroup(
        deviceId: String,
        routeId: String,
        route: JsonObject,
        sales: List<SaleRecord>,
        audit: List<RouteAuditEntry>,
        storeNames: Map<String, String>,
    ) = SyncGroup(
        "route:$routeId",
        listOf(
            "routes" to JsonArray(listOf(route)),
            "sale_lines" to buildJsonArray {
                sales.forEach { sale ->
                    sale.lines.forEachIndexed { index, line ->
                        add(buildJsonObject {
                            put("device_id", deviceId)
                            put("id", "${sale.id}:$index")
                            put("route_id", routeId)
                            put("sale_id", sale.id)
                            put("store_id", sale.storeId)
                            put("store_name", sale.storeId?.let(storeNames::get) ?: sale.customerName)
                            put("product_id", line.product.id)
                            put("product_name", line.product.name)
                            put("requested", line.requested)
                            put("delivered", line.delivered)
                            put("total", line.total)
                            put("recorded_at", iso(sale.recordedAtEpochMillis))
                            put("cancelled", sale.cancelled)
                            put("latitude", sale.location?.latitude)
                            put("longitude", sale.location?.longitude)
                        })
                    }
                }
            },
            "movements" to buildJsonArray {
                audit.forEach { entry ->
                    add(buildJsonObject {
                        put("device_id", deviceId)
                        put("id", entry.id)
                        put("route_id", routeId)
                        put("type", entry.type.name)
                        put("title", entry.title)
                        put("detail", entry.detail)
                        put("cash_delta", entry.cashDelta)
                        put("recorded_at", iso(entry.recordedAtEpochMillis))
                    })
                }
            },
        ),
    )

    fun iso(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))
}
