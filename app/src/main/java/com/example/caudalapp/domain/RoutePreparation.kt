package com.example.caudalapp.domain

object ProductCatalog {
    val BAGS = Product(id = "bags", name = "Bolsa", defaultSuggestedTotal = 50)
    val WATER_JUG = Product(id = "water-jug", name = "Garrafón", defaultSuggestedTotal = 8, returnable = true)
    val GAS_CYLINDER = Product(id = "gas-cylinder", name = "Gas", returnable = true)
    val EGGS = Product(id = "eggs", name = "Huevo")

    val initialProducts = listOf(BAGS, WATER_JUG, GAS_CYLINDER, EGGS)

    fun find(productId: String): Product? = initialProducts.firstOrNull { it.id == productId }
}

data class RouteDraft(
    val name: String,
    val initialCash: Int,
    val stock: Map<String, Int> = emptyMap(),
    val emptyContainers: Map<String, Int> = emptyMap(),
)

data class ActiveRoute(
    val name: String,
    val ledger: RouteLedger,
    val startedAtEpochMillis: Long = System.currentTimeMillis(),
)

data class CompletedRouteRecord(
    val routeName: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val summary: RouteCloseSummary,
    val trackId: String? = null,
    val trackPointCount: Int = 0,
    val sales: List<SaleRecord> = emptyList(),
    val auditEntries: List<RouteAuditEntry> = emptyList(),
    val activeDurationMillis: Long = 0L,
    val countedCash: Int? = null,
    val countedStock: Map<String, Int> = emptyMap(),
    val expectedStock: Map<String, Int> = emptyMap(),
)

enum class RouteField {
    ACTIVE_ROUTE,
    INITIAL_CASH,
    STOCK,
    EMPTY_CONTAINERS,
}

data class RouteValidationError(
    val field: RouteField,
    val message: String,
)

sealed interface RouteStartResult {
    data class Started(val route: ActiveRoute) : RouteStartResult
    data class Rejected(val errors: List<RouteValidationError>) : RouteStartResult
}

object RoutePreparation {
    fun start(
        draft: RouteDraft,
        activeRouteExists: Boolean,
        availableProducts: List<Product> = ProductCatalog.initialProducts,
    ): RouteStartResult {
        if (activeRouteExists) {
            return RouteStartResult.Rejected(
                listOf(RouteValidationError(RouteField.ACTIVE_ROUTE, "Ya existe una ruta activa")),
            )
        }

        val errors = buildList {
            if (draft.initialCash < 0) {
                add(RouteValidationError(RouteField.INITIAL_CASH, "El sencillo no puede ser negativo"))
            }
            if (draft.stock.values.any { it < 0 }) {
                add(RouteValidationError(RouteField.STOCK, "El inventario no puede ser negativo al iniciar"))
            }
            if (draft.emptyContainers.values.any { it < 0 }) {
                add(RouteValidationError(RouteField.EMPTY_CONTAINERS, "Los envases vacíos no pueden ser negativos"))
            }
        }
        if (errors.isNotEmpty()) return RouteStartResult.Rejected(errors)

        val catalogById = availableProducts.associateBy(Product::id)
        val productsById = (draft.stock.keys + draft.emptyContainers.keys)
            .associateWith { id -> catalogById[id] ?: ProductCatalog.find(id) ?: Product(id = id, name = id) }
        val stock = draft.stock.mapKeys { (id, _) -> productsById.getValue(id) }

        return RouteStartResult.Started(
            ActiveRoute(
                name = draft.name.trim().ifEmpty { "Ruta sin nombre" },
                ledger = RouteLedger.start(
                    initialCash = draft.initialCash,
                    initialStock = stock,
                    initialEmptyContainers = draft.emptyContainers,
                ),
            ),
        )
    }
}
