package com.example.caudalapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SalePaymentPolicyTest {

    @Test
    fun `sin activar fiado se cobra el total de la factura`() {
        assertEquals(
            50,
            SalePaymentPolicy.amountReceived(
                invoiceTotal = 50,
                creditEnabled = false,
                typedAmount = null,
                hasPendingDelivery = false,
            ),
        )
    }

    @Test
    fun `fiado sin cantidad significa cero recibido`() {
        assertEquals(
            0,
            SalePaymentPolicy.amountReceived(50, creditEnabled = true, typedAmount = null, hasPendingDelivery = false),
        )
    }

    @Test
    fun `fiado permite registrar un pago parcial`() {
        assertEquals(
            30,
            SalePaymentPolicy.amountReceived(50, creditEnabled = true, typedAmount = 30, hasPendingDelivery = false),
        )
    }

    @Test
    fun `una entrega incompleta no se cobra aunque contado este seleccionado`() {
        assertEquals(
            0,
            SalePaymentPolicy.amountReceived(50, creditEnabled = false, typedAmount = null, hasPendingDelivery = true),
        )
    }
}
