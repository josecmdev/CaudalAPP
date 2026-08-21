package com.example.caudalapp.domain

object SuggestedPricePolicy {
    fun forQuantity(product: Product, quantity: Int): Int? {
        if (quantity <= 0) return null
        return when (product.id) {
            // La tarifa habitual se cobra por grupos completos de 18 bolsas.
            // Una cantidad parcial queda libre para evitar inventar un precio.
            ProductCatalog.BAGS.id -> quantity.takeIf { it % 18 == 0 }?.let { it / 18 * 50 }
            ProductCatalog.WATER_JUG.id -> quantity * 8
            else -> product.defaultSuggestedTotal
        }
    }
}

object StorePriceSuggestionPolicy {
    fun forSale(
        storeId: String,
        product: Product,
        quantity: Int,
        sales: List<SaleRecord>,
    ): Int? {
        val remembered = sales.asSequence()
            .filter { it.storeId == storeId && !it.cancelled }
            .sortedByDescending(SaleRecord::recordedAtEpochMillis)
            .flatMap { it.lines.asSequence() }
            .firstOrNull { it.product.id == product.id && it.requested == quantity }
            ?.total
        return remembered ?: SuggestedPricePolicy.forQuantity(product, quantity)
    }
}
