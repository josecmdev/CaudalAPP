package com.example.caudalapp.domain

object QuantityInput {
    const val MAX_DIGITS = 7

    fun sanitize(text: String): String = text.filter(Char::isDigit).take(MAX_DIGITS)

    fun step(text: String, delta: Int): String {
        val next = ((text.toIntOrNull() ?: 0) + delta).coerceIn(0, 9_999_999)
        return if (next == 0) "" else next.toString()
    }
}
