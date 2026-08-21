package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDirectoryTest {
    @Test
    fun `crea edita archiva y restaura un producto local`() {
        var nextId = 0
        val directory = ProductDirectory(emptyList()) { "custom-product-${nextId++}" }
        val created = directory.addProduct("Refresco", 10, false, "content://image") as ProductChangeResult.Success

        directory.updateProduct(created.product.id, "Refresco grande", 12, true, "content://new-image")
        assertEquals("Refresco grande", directory.get(created.product.id)?.name)
        assertEquals(12, directory.get(created.product.id)?.defaultSuggestedTotal)
        assertTrue(directory.get(created.product.id)?.returnable == true)

        // Es el único activo, por lo que no puede dejar el catálogo vacío.
        assertFalse(directory.archiveProduct(created.product.id))
        directory.addProduct("Segundo", null, false)
        assertTrue(directory.archiveProduct(created.product.id))
        assertTrue(directory.restoreProduct(created.product.id))
    }

    @Test
    fun `rechaza nombre vacio y precio negativo`() {
        val directory = ProductDirectory()

        assertTrue(directory.addProduct(" ", 10, false) is ProductChangeResult.Rejected)
        assertTrue(directory.addProduct("Producto", -1, false) is ProductChangeResult.Rejected)
    }
}
