package com.example.caudalapp

import java.lang.reflect.Modifier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.ProductCatalog
import com.example.caudalapp.domain.RouteLedger

class CaudalViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `android puede crear el viewmodel mediante su constructor publico`() {
        val constructor = CaudalViewModel::class.java.getDeclaredConstructor()

        assertTrue(Modifier.isPublic(CaudalViewModel::class.java.modifiers))
        assertTrue(Modifier.isPublic(constructor.modifiers))
        assertNotNull(constructor.newInstance())
        assertTrue(constructor.newInstance().mapVisible)
    }

    @Test
    fun `un viewmodel nuevo recupera la ruta activa guardada localmente`() {
        val file = temporaryFolder.newFile("state.json")
        val first = CaudalViewModel()
        first.attachPersistence(file)
        first.activeRoute = ActiveRoute(
            name = "Viernes",
            ledger = RouteLedger.start(100, mapOf(ProductCatalog.BAGS to 20)),
            startedAtEpochMillis = 55L,
        )
        first.persist()

        val restored = CaudalViewModel()
        restored.attachPersistence(file)

        assertNotNull(restored.activeRoute)
        assertTrue(restored.activeRoute?.name == "Viernes")
        assertTrue(restored.activeRoute?.ledger?.availableStock(ProductCatalog.BAGS.id) == 20)
    }
}
