package com.example.caudalapp.domain

object SalePaymentPolicy {
    fun amountReceived(
        invoiceTotal: Int,
        creditEnabled: Boolean,
        typedAmount: Int?,
        hasPendingDelivery: Boolean,
    ): Int {
        require(invoiceTotal >= 0)
        // Jose cobra al momento aunque falte producto por entregar; el fiado sigue siendo opcional.
        return if (creditEnabled) typedAmount ?: 0 else invoiceTotal
    }
}
