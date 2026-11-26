package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityInputTest {

    @Test
    fun `deja solo digitos y maximo siete`() {
        assertEquals("1234567", QuantityInput.sanitize("Q 1,234,567.89"))
        assertEquals("", QuantityInput.sanitize("-abc"))
    }

    @Test
    fun `sumar y restar parte de vacio y nunca baja de cero`() {
        assertEquals("1", QuantityInput.step("", 1))
        assertEquals("11", QuantityInput.step("10", 1))
        assertEquals("", QuantityInput.step("1", -1))
        assertEquals("", QuantityInput.step("", -1))
    }

    @Test
    fun `no pasa del maximo de siete digitos`() {
        assertEquals("9999999", QuantityInput.step("9999999", 1))
    }
}
