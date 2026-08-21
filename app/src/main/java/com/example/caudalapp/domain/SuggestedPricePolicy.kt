package com.example.caudalapp.domain

object SuggestedPricePolicy {
    fun forQuantity(product: Product, quantity: Int): Int? {
        if (quantity <= 0) return null
        return when (product.id) {
            ProductCatalog.BAGS.id -> when {
                quantity == 8 -> 25
                quantity % 18 == 0 -> quantity / 18 * 50
                else -> null
            }
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
