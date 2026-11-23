package com.example.caudalapp

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePreparationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val products = listOf(
        Product(id = "bolsa", name = "Bolsa"),
        Product(id = "garrafon", name = "Garrafón", returnable = true),
    )

    private fun show(onStarted: (ActiveRoute) -> Unit = {}, onBack: () -> Unit = {}) {
        compose.setContent { RoutePreparationScreen(products = products, onBack = onBack, onRouteStarted = onStarted) }
    }

    @Test
    fun sumarYRestarCambiaLaCantidadSinBajarDeCero() {
        show()
        compose.onNodeWithContentDescription("Sumar Bolsa").performClick().performClick()
        compose.onNodeWithTag("qty_bolsa").assertTextContains("2")
        compose.onNodeWithContentDescription("Restar Bolsa").performClick().performClick().performClick()
        compose.onNodeWithText("0 de 2 productos cargados", substring = true).assertExists()
    }

    @Test
    fun escribirSoloAceptaNumeros() {
        show()
        compose.onNodeWithTag("qty_garrafon").performTextInput("1a2")
        compose.onNodeWithTag("qty_garrafon").assertTextContains("12")
    }

    @Test
    fun iniciarRutaEnviaNombreSencilloEInventario() {
        var started: ActiveRoute? = null
        show(onStarted = { started = it })
        compose.onNodeWithText("Jueves").performClick()
        compose.onNodeWithText("Q200").performClick()
        compose.onNodeWithContentDescription("Sumar Garrafón").performClick()
        compose.onNodeWithTag("qty_bolsa").performTextInput("30")
        compose.onNodeWithText("2 de 2 productos cargados · Q200 de sencillo").assertExists()
        compose.onNodeWithText("Iniciar ruta").performClick()

        val route = requireNotNull(started)
        assertEquals("Jueves", route.name)
        assertEquals(200, route.ledger.initialCash)
        assertEquals(30, route.ledger.availableStock("bolsa"))
        assertEquals(1, route.ledger.availableStock("garrafon"))
    }

    @Test
    fun volverLlamaAlRegreso() {
        var back = false
        show(onBack = { back = true })
        compose.onNodeWithContentDescription("Volver").performClick()
        assertTrue(back)
    }
}
