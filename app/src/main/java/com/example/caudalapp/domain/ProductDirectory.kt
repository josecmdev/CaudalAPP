package com.example.caudalapp.domain

import java.util.UUID

sealed interface ProductChangeResult {
    data class Success(val product: Product) : ProductChangeResult
    data class Rejected(val message: String) : ProductChangeResult
}

class ProductDirectory(
    initialProducts: List<Product> = ProductCatalog.initialProducts,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val products = linkedMapOf<String, Product>().apply {
        initialProducts.forEach { product -> put(product.id, product.copy()) }
    }

    fun addProduct(
        name: String,
        suggestedTotal: Int?,
        returnable: Boolean,
        imageUri: String? = null,
    ): ProductChangeResult {
        if (name.isBlank()) return ProductChangeResult.Rejected("Escribe el nombre del producto")
        if (suggestedTotal != null && suggestedTotal < 0) {
            return ProductChangeResult.Rejected("El precio sugerido no puede ser negativo")
        }
        val product = Product(
            id = idGenerator(),
            name = name.trim(),
            defaultSuggestedTotal = suggestedTotal,
            returnable = returnable,
            imageUri = imageUri,
        )
        products[product.id] = product
        return ProductChangeResult.Success(product)
    }

    fun updateProduct(
        productId: String,
        name: String,
        suggestedTotal: Int?,
        returnable: Boolean,
        imageUri: String?,
    ): ProductChangeResult {
        val current = products[productId] ?: return ProductChangeResult.Rejected("El producto ya no existe")
        if (name.isBlank()) return ProductChangeResult.Rejected("Escribe el nombre del producto")
        if (suggestedTotal != null && suggestedTotal < 0) {
            return ProductChangeResult.Rejected("El precio sugerido no puede ser negativo")
        }
        val updated = current.copy(
            name = name.trim(),
            defaultSuggestedTotal = suggestedTotal,
            returnable = returnable,
            imageUri = imageUri,
        )
        products[productId] = updated
        return ProductChangeResult.Success(updated)
    }

    fun archiveProduct(productId: String): Boolean {
        val current = products[productId] ?: return false
        if (!current.archived && activeProducts().size == 1) return false
        products[productId] = current.copy(archived = true)
        return true
    }

    fun restoreProduct(productId: String): Boolean {
        val current = products[productId] ?: return false
        products[productId] = current.copy(archived = false)
        return true
    }

    fun get(productId: String): Product? = products[productId]

    fun activeProducts(): List<Product> = products.values.filterNot(Product::archived)

    fun allProducts(): List<Product> = products.values.toList()

    companion object {
        fun restore(products: List<Product>): ProductDirectory = ProductDirectory(
            initialProducts = products.ifEmpty { ProductCatalog.initialProducts },
        )
    }
}
