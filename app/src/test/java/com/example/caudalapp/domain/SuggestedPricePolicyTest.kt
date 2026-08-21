package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestedPricePolicyTest {

    @Test
    fun `sugiere los totales habituales exactos para bolsas`() {
        assertEquals(50, SuggestedPricePolicy.forQuantity(ProductCatalog.BAGS, 18))
        assertEquals(100, SuggestedPricePolicy.forQuantity(ProductCatalog.BAGS, 36))
        assertEquals(150, SuggestedPricePolicy.forQuantity(ProductCatalog.BAGS, 72))
    }

    @Test
    fun `una cantidad de bolsas no habitual queda libre para escribir el total`() {
        assertNull(SuggestedPricePolicy.forQuantity(ProductCatalog.BAGS, 15))
    }

    @Test
    fun `el agua sugiere ocho quetzales por garrafon`() {
        assertEquals(8, SuggestedPricePolicy.forQuantity(ProductCatalog.WATER_JUG, 1))
        assertEquals(24, SuggestedPricePolicy.forQuantity(ProductCatalog.WATER_JUG, 3))
    }

    @Test
    fun `recuerda el ultimo total de la misma tienda producto y cantidad`() {
        val product = ProductCatalog.BAGS
        val older = SaleRecord(
            id = "old",
            storeId = "store-price",
            lines = listOf(SaleLine(product, 18, 18, 45)),
            amountReceived = 45,
            recordedAtEpochMillis = 10L,
        )
        val latest = older.copy(id = "new", lines = listOf(SaleLine(product, 18, 18, 48)), recordedAtEpochMillis = 20L)

        assertEquals(48, StorePriceSuggestionPolicy.forSale("store-price", product, 18, listOf(older, latest)))
        assertEquals(100, StorePriceSuggestionPolicy.forSale("store-price", product, 36, listOf(older, latest)))
    }
}
