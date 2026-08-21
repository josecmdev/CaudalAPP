package com.example.caudalapp.domain

import java.util.UUID

enum class StoreAccountAdjustmentType {
    MONEY_PAYMENT,
    CONTAINER_RETURN,
    DELIVERY_COMPLETED,
}

data class StoreAccountAdjustment(
    val id: String = UUID.randomUUID().toString(),
    val storeId: String,
    val type: StoreAccountAdjustmentType,
    val amount: Int = 0,
    val productId: String? = null,
    val quantity: Int = 0,
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
)

object AccountSettlementPolicy {
    fun moneyDue(account: StoreAccountState): Int =
        account.debts.sumOf(Debt::remainingAmount) + account.pendingPayments.values.sum()

    fun registerPayment(account: StoreAccountState, requestedAmount: Int): StoreAccountState {
        var amount = requestedAmount.coerceIn(0, moneyDue(account))
        val debts = account.debts.mapNotNull { debt ->
            val applied = minOf(amount, debt.remainingAmount)
            amount -= applied
            debt.copy(remainingAmount = debt.remainingAmount - applied).takeIf { it.remainingAmount > 0 }
        }
        val payments = account.pendingPayments.mapValues { (_, due) ->
            val applied = minOf(amount, due)
            amount -= applied
            due - applied
        }.filterValues { it > 0 }
        return account.copy(debts = debts, pendingPayments = payments)
    }

    fun completeDelivery(account: StoreAccountState, productId: String): StoreAccountState =
        account.copy(pendingDeliveries = account.pendingDeliveries - productId)

    fun receiveContainers(account: StoreAccountState, productId: String): StoreAccountState =
        account.copy(pendingContainers = account.pendingContainers - productId)

    fun hasPending(account: StoreAccountState): Boolean =
        moneyDue(account) > 0 || account.pendingDeliveries.values.any { it > 0 } ||
            account.pendingContainers.values.any { it > 0 }
}
