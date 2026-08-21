package com.example.caudalapp.domain

object SalePaymentPolicy {
    fun amountReceived(
        invoiceTotal: Int,
        creditEnabled: Boolean,
        typedAmount: Int?,
        hasPendingDelivery: Boolean,
    ): Int {
        require(invoiceTotal >= 0)
        if (hasPendingDelivery) return 0
        return if (creditEnabled) typedAmount ?: 0 else invoiceTotal
    }
}
