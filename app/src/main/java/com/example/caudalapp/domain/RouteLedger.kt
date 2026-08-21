package com.example.caudalapp.domain

import java.util.UUID

data class Product(
    val id: String,
    val name: String,
    val defaultSuggestedTotal: Int? = null,
    val returnable: Boolean = false,
    val imageUri: String? = null,
    val archived: Boolean = false,
)

data class SaleLine(
    val product: Product,
    val requested: Int,
    val delivered: Int,
    val total: Int,
    val emptyContainersReceived: Int = 0,
    val containersSold: Int = 0,
) {
    init {
        require(requested > 0) { "La cantidad solicitada debe ser positiva" }
        require(delivered in 0..requested) { "La cantidad entregada no puede superar la solicitada" }
        require(total >= 0) { "El total no puede ser negativo" }
        require(emptyContainersReceived >= 0) { "Los envases recibidos no pueden ser negativos" }
        require(containersSold in 0..delivered) { "No se pueden vender más envases que llenos entregados" }
        require(product.returnable || (emptyContainersReceived == 0 && containersSold == 0)) {
            "Solo los productos retornables administran envases"
        }
    }
}

data class Debt(
    val originalAmount: Int,
    var remainingAmount: Int,
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(originalAmount > 0)
        require(remainingAmount in 0..originalAmount)
    }
}

data class StoreAccount(
    val storeId: String,
    val openDebts: MutableList<Debt> = mutableListOf(),
    val pendingDeliveries: MutableMap<String, Int> = mutableMapOf(),
    val pendingContainers: MutableMap<String, Int> = mutableMapOf(),
    val pendingPayments: MutableMap<String, Int> = mutableMapOf(),
) {
    val moneyDue: Int
        get() = openDebts.sumOf { it.remainingAmount }

    val hasPendingDelivery: Boolean
        get() = pendingDeliveries.values.any { it > 0 }

    fun unitsPending(productId: String): Int = pendingDeliveries[productId] ?: 0

    fun containersDue(productId: String): Int = pendingContainers[productId] ?: 0

    fun paymentPending(productId: String): Int = pendingPayments[productId] ?: 0
}

data class QuickSaleResult(
    val accepted: Boolean,
    val reason: String? = null,
)

enum class ExpenseCategory(val displayName: String) {
    FUEL("Combustible"),
    REFRESHMENT("Refacción"),
    OTHER("Otro"),
}

data class RouteExpense(
    val category: ExpenseCategory,
    val amount: Int,
    val id: String = UUID.randomUUID().toString(),
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
    var cancelledAtEpochMillis: Long? = null,
    var cancellationReason: String? = null,
) {
    val cancelled: Boolean get() = cancelledAtEpochMillis != null
}

enum class TransferDirection {
    TO_KENNETH,
    FROM_KENNETH,
}

data class TransferLine(
    val product: Product,
    val fullUnits: Int = 0,
    val emptyUnits: Int = 0,
) {
    init {
        require(fullUnits >= 0 && emptyUnits >= 0) { "Las cantidades no pueden ser negativas" }
        require(fullUnits > 0 || emptyUnits > 0) { "La transferencia está vacía" }
        require(product.returnable || emptyUnits == 0) { "Solo un retornable puede transferir envases vacíos" }
    }
}

data class KennethTransfer(
    val direction: TransferDirection,
    val lines: List<TransferLine>,
    val recordedAtEpochMillis: Long,
)

data class StoreAccountState(
    val storeId: String,
    val debts: List<Debt>,
    val pendingDeliveries: Map<String, Int>,
    val pendingContainers: Map<String, Int>,
    val pendingPayments: Map<String, Int>,
)

data class RouteLedgerState(
    val initialCash: Int,
    val products: List<Product>,
    val stock: Map<String, Int>,
    val emptyContainers: Map<String, Int>,
    val accounts: List<StoreAccountState>,
    val cashOnHand: Int,
    val totalSales: Int,
    val cashCollected: Int,
    val totalExpenses: Int,
    val expenses: List<RouteExpense>,
    val kennethTransfers: List<KennethTransfer>,
    val sales: List<SaleRecord> = emptyList(),
    val auditEntries: List<RouteAuditEntry> = emptyList(),
)

enum class RouteAuditType {
    STORE_SALE,
    QUICK_SALE,
    SALE_CANCELLED,
    DEBT_PAYMENT,
    CONTAINER_RETURN,
    DELIVERY_COMPLETED,
    RESTOCK,
    EMPTY_CONTAINERS_UNLOADED,
    EXPENSE,
    EXPENSE_CANCELLED,
    KENNETH_TRANSFER,
}

data class RouteAuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: RouteAuditType,
    val title: String,
    val detail: String,
    val cashDelta: Int = 0,
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
    val relatedSaleId: String? = null,
    val relatedExpenseId: String? = null,
)

data class SaleRecord(
    val id: String,
    val storeId: String?,
    val lines: List<SaleLine>,
    val amountReceived: Int,
    val recordedAtEpochMillis: Long,
    val debtId: String? = null,
    val pendingDeliveriesAdded: Map<String, Int> = emptyMap(),
    val pendingContainersAdded: Map<String, Int> = emptyMap(),
    val pendingPaymentProductId: String? = null,
    val pendingPaymentAdded: Int = 0,
    var cancelledAtEpochMillis: Long? = null,
    var cancellationReason: String? = null,
    val location: GeoPoint? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
) {
    val total: Int get() = lines.sumOf(SaleLine::total)
    val cancelled: Boolean get() = cancelledAtEpochMillis != null
}

data class SaleCancellationResult(val accepted: Boolean, val reason: String? = null)

data class RouteCloseSummary(
    val initialCash: Int,
    val totalSales: Int,
    val cashCollected: Int,
    val moneyPending: Int,
    val totalExpenses: Int,
    val expectedCash: Int,
    val pendingDeliveryUnits: Int,
    val pendingContainers: Int,
    val negativeStock: Map<String, Int>,
) {
    val hasWarnings: Boolean
        get() = pendingDeliveryUnits > 0 || pendingContainers > 0 || negativeStock.isNotEmpty()
}

class RouteLedger private constructor(
    val initialCash: Int,
    initialStock: Map<Product, Int>,
    initialEmptyContainers: Map<String, Int>,
) {
    private val products = initialStock.keys.associateBy(Product::id).toMutableMap()
    private val stock = initialStock.entries.associate { it.key.id to it.value }.toMutableMap()
    private val emptyContainerStock = initialEmptyContainers.toMutableMap()
    private val accounts = mutableMapOf<String, StoreAccount>()

    var cashOnHand: Int = initialCash
        private set
    var totalSales: Int = 0
        private set
    var cashCollected: Int = 0
        private set
    var totalExpenses: Int = 0
        private set
    private val expenseEntries = mutableListOf<RouteExpense>()
    val expenses: List<RouteExpense>
        get() = expenseEntries.map(RouteExpense::copy)
    private val transferEntries = mutableListOf<KennethTransfer>()
    val kennethTransfers: List<KennethTransfer>
        get() = transferEntries.toList()
    private val saleEntries = mutableListOf<SaleRecord>()
    val sales: List<SaleRecord>
        get() = saleEntries.map { it.copy(lines = it.lines.map(SaleLine::copy)) }
    private val auditLog = mutableListOf<RouteAuditEntry>()
    val auditEntries: List<RouteAuditEntry>
        get() = auditLog.toList()

    init {
        require(initialCash >= 0) { "El sencillo inicial no puede ser negativo" }
        require(initialStock.values.all { it >= 0 }) { "El inventario inicial no puede ser negativo" }
        require(initialEmptyContainers.values.all { it >= 0 }) { "Los envases iniciales no pueden ser negativos" }
    }

    fun registerStoreSale(
        storeId: String,
        lines: List<SaleLine>,
        amountReceived: Int,
        location: GeoPoint? = null,
    ): RouteLedger {
        require(storeId.isNotBlank()) { "La venta fiada o de tienda necesita una tienda" }
        registerSale(storeId = storeId, lines = lines, amountReceived = amountReceived, location = location)
        return this
    }

    fun tryRegisterQuickSale(
        lines: List<SaleLine>,
        amountReceived: Int,
        location: GeoPoint? = null,
        customerName: String? = null,
        customerPhone: String? = null,
    ): QuickSaleResult {
        if (lines.isEmpty()) {
            return QuickSaleResult(accepted = false, reason = "Agrega al menos un producto")
        }
        val total = lines.sumOf(SaleLine::total)
        if (amountReceived != total) {
            return QuickSaleResult(
                accepted = false,
                reason = "Las ventas rápidas deben pagarse completamente al contado",
            )
        }
        val unsettledReturnable = lines.firstOrNull {
            it.product.returnable && it.emptyContainersReceived + it.containersSold < it.delivered
        }
        if (unsettledReturnable != null) {
            return QuickSaleResult(
                accepted = false,
                reason = "Una venta rápida no puede dejar envases prestados",
            )
        }
        registerSale(
            storeId = null,
            lines = lines,
            amountReceived = amountReceived,
            location = location,
            customerName = customerName,
            customerPhone = customerPhone,
        )
        return QuickSaleResult(accepted = true)
    }

    private fun registerSale(
        storeId: String?,
        lines: List<SaleLine>,
        amountReceived: Int,
        location: GeoPoint? = null,
        customerName: String? = null,
        customerPhone: String? = null,
    ) {
        require(lines.isNotEmpty()) { "La venta necesita al menos un producto" }
        val total = lines.sumOf(SaleLine::total)
        require(amountReceived in 0..total) { "El efectivo recibido debe estar entre cero y el total" }

        val saleId = UUID.randomUUID().toString()
        val pendingDeliveriesAdded = mutableMapOf<String, Int>()
        val pendingContainersAdded = mutableMapOf<String, Int>()
        var pendingPaymentProductId: String? = null
        var pendingPaymentAdded = 0
        var debtId: String? = null
        totalSales += total
        cashCollected += amountReceived
        val account = storeId?.let(::storeAccount)
        lines.forEach { line ->
            products[line.product.id] = line.product
            stock[line.product.id] = availableStock(line.product.id) - line.delivered

            val missingUnits = line.requested - line.delivered
            if (missingUnits > 0 && account != null) {
                account.pendingDeliveries.merge(line.product.id, missingUnits, Int::plus)
                pendingDeliveriesAdded.merge(line.product.id, missingUnits, Int::plus)
            }

            if (line.product.returnable) {
                emptyContainerStock.merge(line.product.id, line.emptyContainersReceived, Int::plus)
                val containersNotReturned =
                    (line.delivered - line.emptyContainersReceived - line.containersSold).coerceAtLeast(0)
                if (containersNotReturned > 0 && account != null) {
                    account.pendingContainers.merge(line.product.id, containersNotReturned, Int::plus)
                    pendingContainersAdded.merge(line.product.id, containersNotReturned, Int::plus)
                }
            }
        }

        cashOnHand += amountReceived
        val hasIncompleteDelivery = lines.any { it.delivered < it.requested }
        val unpaid = total - amountReceived
        if (unpaid > 0 && account != null) {
            if (hasIncompleteDelivery) {
                val paymentProductId = lines.first { it.delivered < it.requested }.product.id
                account.pendingPayments.merge(paymentProductId, unpaid, Int::plus)
                pendingPaymentProductId = paymentProductId
                pendingPaymentAdded = unpaid
            } else {
                debtId = "$saleId-debt"
                account.openDebts += Debt(originalAmount = unpaid, remainingAmount = unpaid, id = debtId)
            }
        }
        saleEntries += SaleRecord(
            id = saleId,
            storeId = storeId,
            lines = lines.map(SaleLine::copy),
            amountReceived = amountReceived,
            recordedAtEpochMillis = System.currentTimeMillis(),
            debtId = debtId,
            pendingDeliveriesAdded = pendingDeliveriesAdded,
            pendingContainersAdded = pendingContainersAdded,
            pendingPaymentProductId = pendingPaymentProductId,
            pendingPaymentAdded = pendingPaymentAdded,
            location = location,
            customerName = customerName?.trim()?.takeIf(String::isNotEmpty),
            customerPhone = customerPhone?.trim()?.takeIf(String::isNotEmpty),
        )
        auditLog += RouteAuditEntry(
            type = if (storeId == null) RouteAuditType.QUICK_SALE else RouteAuditType.STORE_SALE,
            title = if (storeId == null) "Venta rápida" else "Venta en tienda",
            detail = lines.joinToString { "${it.requested} ${it.product.name}" },
            cashDelta = amountReceived,
            relatedSaleId = saleId,
        )
    }

    fun restock(units: Map<Product, Int>) {
        require(units.values.all { it > 0 }) { "Toda recarga debe ser positiva" }
        units.forEach { (product, quantity) ->
            products[product.id] = product
            stock.merge(product.id, quantity, Int::plus)
        }
        auditLog += RouteAuditEntry(
            type = RouteAuditType.RESTOCK,
            title = "Recarga en sede",
            detail = units.entries.joinToString { "${it.value} ${it.key.name}" },
        )
    }

    fun completeDelivery(storeId: String, productId: String, units: Int, amountReceived: Int) {
        require(units > 0)
        require(amountReceived >= 0)
        val account = storeAccount(storeId)
        val pending = account.unitsPending(productId)
        require(units <= pending) { "No se puede completar más de lo pendiente" }
        val remainingUnits = pending - units
        if (remainingUnits > 0) {
            require(amountReceived == 0) { "La entrega se cobra cuando queda completada" }
        }
        val otherPendingUnits = account.pendingDeliveries
            .filterKeys { it != productId }
            .values.sum()
        val allDeliveriesComplete = remainingUnits == 0 && otherPendingUnits == 0
        val paymentDue = if (allDeliveriesComplete) account.pendingPayments.values.sum() else 0
        if (!allDeliveriesComplete) {
            require(amountReceived == 0) { "La factura se cobra al completar todos los productos" }
        } else {
            require(amountReceived <= paymentDue) { "El cobro no puede superar el total pendiente" }
        }
        auditLog += RouteAuditEntry(
            type = RouteAuditType.DELIVERY_COMPLETED,
            title = "Entrega completada",
            detail = "$units unidades",
            cashDelta = amountReceived,
        )
        stock[productId] = availableStock(productId) - units
        account.pendingDeliveries[productId] = remainingUnits
        cashOnHand += amountReceived
        cashCollected += amountReceived
        if (allDeliveriesComplete) {
            account.pendingPayments.clear()
            val remainingDebt = paymentDue - amountReceived
            if (remainingDebt > 0) {
                account.openDebts += Debt(originalAmount = remainingDebt, remainingAmount = remainingDebt)
            }
        }
    }

    fun collectDebt(storeId: String, amount: Int) {
        require(amount > 0)
        val account = storeAccount(storeId)
        require(amount <= account.moneyDue) { "El cobro no puede superar la deuda" }

        var remainingPayment = amount
        account.openDebts.forEach { debt ->
            if (remainingPayment == 0) return@forEach
            val applied = minOf(debt.remainingAmount, remainingPayment)
            debt.remainingAmount -= applied
            remainingPayment -= applied
        }
        account.openDebts.removeAll { it.remainingAmount == 0 }
        cashOnHand += amount
        cashCollected += amount
        auditLog += RouteAuditEntry(
            type = RouteAuditType.DEBT_PAYMENT,
            title = "Pago de deuda",
            detail = "Cobro registrado",
            cashDelta = amount,
        )
    }

    fun registerExpense(category: ExpenseCategory, amount: Int) {
        require(amount > 0) { "El gasto debe ser positivo" }
        require(amount <= cashOnHand) { "El gasto no puede superar el efectivo disponible" }
        cashOnHand -= amount
        totalExpenses += amount
        val expense = RouteExpense(category, amount)
        expenseEntries += expense
        auditLog += RouteAuditEntry(
            type = RouteAuditType.EXPENSE,
            title = category.displayName,
            detail = "Gasto de ruta",
            cashDelta = -amount,
            recordedAtEpochMillis = expense.recordedAtEpochMillis,
            relatedExpenseId = expense.id,
        )
    }

    fun transferWithKenneth(
        direction: TransferDirection,
        lines: List<TransferLine>,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        require(lines.isNotEmpty()) { "Agrega al menos un producto" }
        if (direction == TransferDirection.TO_KENNETH) {
            lines.forEach { line ->
                require(line.fullUnits <= availableStock(line.product.id)) {
                    "No hay suficiente ${line.product.name}"
                }
                require(line.emptyUnits <= emptyContainers(line.product.id)) {
                    "No hay suficientes envases vacíos de ${line.product.name}"
                }
            }
        }
        lines.forEach { line ->
            products[line.product.id] = line.product
            val multiplier = if (direction == TransferDirection.TO_KENNETH) -1 else 1
            stock[line.product.id] = availableStock(line.product.id) + multiplier * line.fullUnits
            if (line.emptyUnits > 0) {
                emptyContainerStock[line.product.id] = emptyContainers(line.product.id) + multiplier * line.emptyUnits
            }
        }
        transferEntries += KennethTransfer(direction, lines.map { it.copy() }, recordedAtEpochMillis)
        auditLog += RouteAuditEntry(
            type = RouteAuditType.KENNETH_TRANSFER,
            title = if (direction == TransferDirection.TO_KENNETH) "Entregado a Kenneth" else "Recibido de Kenneth",
            detail = lines.joinToString { "${it.fullUnits} ${it.product.name}" },
            recordedAtEpochMillis = recordedAtEpochMillis,
        )
    }

    fun closeSummary(): RouteCloseSummary {
        val pendingMoney = accounts.values.sumOf { account ->
            account.moneyDue + account.pendingPayments.values.sum()
        }
        val negative = products.values
            .distinctBy(Product::id)
            .mapNotNull { product ->
                displayStock(product.id).takeIf { it < 0 }?.let { product.name to it }
            }
            .toMap()
        return RouteCloseSummary(
            initialCash = initialCash,
            totalSales = totalSales,
            cashCollected = cashCollected,
            moneyPending = pendingMoney,
            totalExpenses = totalExpenses,
            expectedCash = cashOnHand,
            pendingDeliveryUnits = accounts.values.sumOf { it.pendingDeliveries.values.sum() },
            pendingContainers = accounts.values.sumOf { it.pendingContainers.values.sum() },
            negativeStock = negative,
        )
    }

    fun exportState(): RouteLedgerState = RouteLedgerState(
        initialCash = initialCash,
        products = products.values.toList(),
        stock = stock.toMap(),
        emptyContainers = emptyContainerStock.toMap(),
        accounts = accounts.values.map { account ->
            StoreAccountState(
                storeId = account.storeId,
                debts = account.openDebts.map { it.copy() },
                pendingDeliveries = account.pendingDeliveries.toMap(),
                pendingContainers = account.pendingContainers.toMap(),
                pendingPayments = account.pendingPayments.toMap(),
            )
        },
        cashOnHand = cashOnHand,
        totalSales = totalSales,
        cashCollected = cashCollected,
        totalExpenses = totalExpenses,
        expenses = expenseEntries.toList(),
        kennethTransfers = transferEntries.toList(),
        sales = sales,
        auditEntries = auditEntries,
    )

    fun outstandingAccountStates(): List<StoreAccountState> = accounts.values
        .filter { account ->
            account.moneyDue > 0 ||
                account.pendingDeliveries.values.any { it > 0 } ||
                account.pendingContainers.values.any { it > 0 } ||
                account.pendingPayments.values.any { it > 0 }
        }
        .map(StoreAccount::toState)

    fun importAccounts(savedAccounts: List<StoreAccountState>) {
        savedAccounts.forEach { saved ->
            accounts[saved.storeId] = saved.toAccount()
        }
    }

    fun receiveContainers(storeId: String, productId: String, quantity: Int) {
        require(quantity > 0)
        val account = storeAccount(storeId)
        val due = account.containersDue(productId)
        account.pendingContainers[productId] = (due - quantity).coerceAtLeast(0)
        emptyContainerStock.merge(productId, quantity, Int::plus)
        auditLog += RouteAuditEntry(
            type = RouteAuditType.CONTAINER_RETURN,
            title = "Envases recibidos",
            detail = "$quantity envases",
        )
    }

    fun availableStock(productId: String): Int = stock[productId] ?: 0

    fun products(): List<Product> = products.values.toList()

    fun registerProducts(catalog: List<Product>) {
        catalog.forEach { product -> products.putIfAbsent(product.id, product) }
    }

    fun displayStock(productId: String): Int {
        val pending = accounts.values.sumOf { it.unitsPending(productId) }
        return availableStock(productId) - pending
    }

    fun emptyContainers(productId: String): Int = emptyContainerStock[productId] ?: 0

    fun paymentDueAfterCompleting(storeId: String, productId: String, units: Int): Int {
        val account = storeAccount(storeId)
        val pending = account.unitsPending(productId)
        if (units != pending) return 0
        val otherPending = account.pendingDeliveries.filterKeys { it != productId }.values.sum()
        return if (otherPending == 0) account.pendingPayments.values.sum() else 0
    }

    fun unloadEmptyContainers(productId: String, quantity: Int) {
        require(quantity > 0)
        val available = emptyContainers(productId)
        require(quantity <= available) { "No se pueden descargar más envases de los disponibles" }
        emptyContainerStock[productId] = available - quantity
        auditLog += RouteAuditEntry(
            type = RouteAuditType.EMPTY_CONTAINERS_UNLOADED,
            title = "Vacíos descargados",
            detail = "$quantity envases",
        )
    }

    fun cancelSale(
        saleId: String,
        reason: String,
        cancelledAtEpochMillis: Long = System.currentTimeMillis(),
    ): SaleCancellationResult {
        val sale = saleEntries.firstOrNull { it.id == saleId }
            ?: return SaleCancellationResult(false, "La venta ya no existe")
        if (sale.cancelled) return SaleCancellationResult(false, "La venta ya fue anulada")
        if (reason.isBlank()) return SaleCancellationResult(false, "Escribe el motivo de la anulación")
        if (cashOnHand < sale.amountReceived) {
            return SaleCancellationResult(false, "El efectivo de esta venta ya fue utilizado")
        }
        val account = sale.storeId?.let(::storeAccount)
        val debt = sale.debtId?.let { debtId -> account?.openDebts?.firstOrNull { it.id == debtId } }
        if (sale.debtId != null && (debt == null || debt.remainingAmount != debt.originalAmount)) {
            return SaleCancellationResult(false, "La deuda de esta venta ya tuvo movimientos")
        }
        if (sale.pendingDeliveriesAdded.any { (id, quantity) -> (account?.unitsPending(id) ?: 0) < quantity } ||
            sale.pendingContainersAdded.any { (id, quantity) -> (account?.containersDue(id) ?: 0) < quantity } ||
            (sale.pendingPaymentAdded > 0 &&
                (account?.paymentPending(sale.pendingPaymentProductId.orEmpty()) ?: 0) < sale.pendingPaymentAdded)
        ) {
            return SaleCancellationResult(false, "Esta venta ya tuvo entregas, pagos o devoluciones posteriores")
        }
        val emptiesToRemove = sale.lines.groupBy { it.product.id }
            .mapValues { (_, lines) -> lines.sumOf(SaleLine::emptyContainersReceived) }
        if (emptiesToRemove.any { (id, quantity) -> emptyContainers(id) < quantity }) {
            return SaleCancellationResult(false, "Los envases recibidos en esta venta ya fueron descargados o transferidos")
        }

        totalSales -= sale.total
        cashCollected -= sale.amountReceived
        cashOnHand -= sale.amountReceived
        sale.lines.forEach { line ->
            stock[line.product.id] = availableStock(line.product.id) + line.delivered
            if (line.emptyContainersReceived > 0) {
                emptyContainerStock[line.product.id] = emptyContainers(line.product.id) - line.emptyContainersReceived
            }
        }
        sale.pendingDeliveriesAdded.forEach { (id, quantity) ->
            account?.let { it.pendingDeliveries[id] = it.unitsPending(id) - quantity }
        }
        sale.pendingContainersAdded.forEach { (id, quantity) ->
            account?.let { it.pendingContainers[id] = it.containersDue(id) - quantity }
        }
        if (sale.pendingPaymentAdded > 0) {
            val id = sale.pendingPaymentProductId.orEmpty()
            account?.let { it.pendingPayments[id] = it.paymentPending(id) - sale.pendingPaymentAdded }
        }
        if (debt != null) account?.openDebts?.remove(debt)
        sale.cancelledAtEpochMillis = cancelledAtEpochMillis
        sale.cancellationReason = reason.trim()
        auditLog += RouteAuditEntry(
            type = RouteAuditType.SALE_CANCELLED,
            title = "Venta anulada",
            detail = reason.trim(),
            cashDelta = -sale.amountReceived,
            recordedAtEpochMillis = cancelledAtEpochMillis,
            relatedSaleId = sale.id,
        )
        return SaleCancellationResult(true)
    }

    fun cancelExpense(
        expenseId: String,
        reason: String,
        cancelledAtEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val expense = expenseEntries.firstOrNull { it.id == expenseId } ?: return false
        if (expense.cancelled || reason.isBlank()) return false
        cashOnHand += expense.amount
        totalExpenses -= expense.amount
        expense.cancelledAtEpochMillis = cancelledAtEpochMillis
        expense.cancellationReason = reason.trim()
        auditLog += RouteAuditEntry(
            type = RouteAuditType.EXPENSE_CANCELLED,
            title = "Gasto anulado",
            detail = reason.trim(),
            cashDelta = expense.amount,
            recordedAtEpochMillis = cancelledAtEpochMillis,
            relatedExpenseId = expense.id,
        )
        return true
    }

    fun storeAccount(storeId: String): StoreAccount =
        accounts.getOrPut(storeId) { StoreAccount(storeId = storeId) }

    companion object {
        fun start(
            initialCash: Int,
            initialStock: Map<Product, Int>,
            initialEmptyContainers: Map<String, Int> = emptyMap(),
        ): RouteLedger = RouteLedger(initialCash, initialStock, initialEmptyContainers)

        fun restore(state: RouteLedgerState): RouteLedger {
            val productsById = state.products.associateBy(Product::id)
            val initialStock = state.stock.mapNotNull { (productId, quantity) ->
                productsById[productId]?.let { it to quantity }
            }.toMap()
            return RouteLedger(state.initialCash, initialStock, state.emptyContainers).apply {
                cashOnHand = state.cashOnHand
                totalSales = state.totalSales
                cashCollected = state.cashCollected
                totalExpenses = state.totalExpenses
                expenseEntries.clear()
                expenseEntries.addAll(state.expenses)
                transferEntries.clear()
                transferEntries.addAll(state.kennethTransfers)
                saleEntries.clear()
                saleEntries.addAll(state.sales.map { it.copy(lines = it.lines.map(SaleLine::copy)) })
                auditLog.clear()
                auditLog.addAll(state.auditEntries)
                accounts.clear()
                state.accounts.forEach { saved ->
                    accounts[saved.storeId] = StoreAccount(
                        storeId = saved.storeId,
                        openDebts = saved.debts.mapTo(mutableListOf()) { it.copy() },
                        pendingDeliveries = saved.pendingDeliveries.toMutableMap(),
                        pendingContainers = saved.pendingContainers.toMutableMap(),
                        pendingPayments = saved.pendingPayments.toMutableMap(),
                    )
                }
            }
        }
    }
}

private fun StoreAccount.toState() = StoreAccountState(
    storeId = storeId,
    debts = openDebts.map { it.copy() },
    pendingDeliveries = pendingDeliveries.toMap(),
    pendingContainers = pendingContainers.toMap(),
    pendingPayments = pendingPayments.toMap(),
)

private fun StoreAccountState.toAccount() = StoreAccount(
    storeId = storeId,
    openDebts = debts.mapTo(mutableListOf()) { it.copy() },
    pendingDeliveries = pendingDeliveries.toMutableMap(),
    pendingContainers = pendingContainers.toMutableMap(),
    pendingPayments = pendingPayments.toMutableMap(),
)

enum class StoreMarkerState {
    DEBT,
    PENDING_DELIVERY,
    RECENT,
    DUE_SOON,
    INACTIVE,
}

object StoreMarkerPolicy {
    fun resolve(account: StoreAccount, daysSinceLastSale: Int?): StoreMarkerState = when {
        account.moneyDue > 0 -> StoreMarkerState.DEBT
        account.hasPendingDelivery -> StoreMarkerState.PENDING_DELIVERY
        daysSinceLastSale != null && daysSinceLastSale <= 3 -> StoreMarkerState.RECENT
        daysSinceLastSale != null && daysSinceLastSale <= 7 -> StoreMarkerState.DUE_SOON
        else -> StoreMarkerState.INACTIVE
    }

    fun daysSinceLastSale(
        storeId: String,
        sales: List<SaleRecord>,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Int? {
        val latest = sales.asSequence()
            .filter { it.storeId == storeId && !it.cancelled }
            .maxOfOrNull(SaleRecord::recordedAtEpochMillis)
            ?: return null
        return ((nowEpochMillis - latest).coerceAtLeast(0L) / 86_400_000L).toInt()
    }
}
