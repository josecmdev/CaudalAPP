package com.example.caudalapp.persistence

import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.CompletedRouteRecord
import com.example.caudalapp.domain.Debt
import com.example.caudalapp.domain.ExpenseCategory
import com.example.caudalapp.domain.Product
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.RouteCloseSummary
import com.example.caudalapp.domain.RouteExpense
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.RouteLedgerState
import com.example.caudalapp.domain.RouteAuditEntry
import com.example.caudalapp.domain.RouteAuditType
import com.example.caudalapp.domain.SaleLine
import com.example.caudalapp.domain.SaleRecord
import com.example.caudalapp.domain.Store
import com.example.caudalapp.domain.StoreAccountState
import com.example.caudalapp.domain.StoreDirectory
import com.example.caudalapp.domain.KennethTransfer
import com.example.caudalapp.domain.TransferDirection
import com.example.caudalapp.domain.TransferLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class CaudalRestoredState(
    val activeRoute: ActiveRoute?,
    val stores: StoreDirectory,
    val completedRoutes: List<CompletedRouteRecord>,
    val outstandingAccounts: List<StoreAccountState> = emptyList(),
    val products: ProductDirectory = ProductDirectory(),
)

@Serializable
private data class ProductDto(
    val id: String,
    val name: String,
    val defaultSuggestedTotal: Int? = null,
    val returnable: Boolean = false,
    val imageUri: String? = null,
    val archived: Boolean = false,
)

@Serializable
private data class DebtDto(val originalAmount: Int, val remainingAmount: Int, val id: String = "")

@Serializable
private data class SaleLineDto(
    val product: ProductDto,
    val requested: Int,
    val delivered: Int,
    val total: Int,
    val emptyContainersReceived: Int = 0,
    val containersSold: Int = 0,
)

@Serializable
private data class SaleRecordDto(
    val id: String,
    val storeId: String? = null,
    val lines: List<SaleLineDto>,
    val amountReceived: Int,
    val recordedAtEpochMillis: Long,
    val debtId: String? = null,
    val pendingDeliveriesAdded: Map<String, Int> = emptyMap(),
    val pendingContainersAdded: Map<String, Int> = emptyMap(),
    val pendingPaymentProductId: String? = null,
    val pendingPaymentAdded: Int = 0,
    val cancelledAtEpochMillis: Long? = null,
    val cancellationReason: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
)

@Serializable
private data class AuditEntryDto(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val cashDelta: Int,
    val recordedAtEpochMillis: Long,
    val relatedSaleId: String? = null,
    val relatedExpenseId: String? = null,
)

@Serializable
private data class AccountDto(
    val storeId: String,
    val debts: List<DebtDto>,
    val pendingDeliveries: Map<String, Int>,
    val pendingContainers: Map<String, Int>,
    val pendingPayments: Map<String, Int>,
)

@Serializable
private data class ExpenseDto(
    val category: String,
    val amount: Int,
    val id: String = "",
    val recordedAtEpochMillis: Long = 0L,
    val cancelledAtEpochMillis: Long? = null,
    val cancellationReason: String? = null,
)

@Serializable
private data class TransferLineDto(
    val product: ProductDto,
    val fullUnits: Int,
    val emptyUnits: Int,
)

@Serializable
private data class KennethTransferDto(
    val direction: String,
    val lines: List<TransferLineDto>,
    val recordedAtEpochMillis: Long,
)

@Serializable
private data class LedgerDto(
    val initialCash: Int,
    val products: List<ProductDto>,
    val stock: Map<String, Int>,
    val emptyContainers: Map<String, Int>,
    val accounts: List<AccountDto>,
    val cashOnHand: Int,
    val totalSales: Int,
    val cashCollected: Int,
    val totalExpenses: Int,
    val expenses: List<ExpenseDto>,
    val kennethTransfers: List<KennethTransferDto> = emptyList(),
    val sales: List<SaleRecordDto> = emptyList(),
    val auditEntries: List<AuditEntryDto> = emptyList(),
)

@Serializable
private data class ActiveRouteDto(
    val name: String,
    val startedAtEpochMillis: Long,
    val ledger: LedgerDto,
)

@Serializable
private data class StoreDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val phone: String? = null,
    val archived: Boolean = false,
)

@Serializable
private data class CloseSummaryDto(
    val initialCash: Int,
    val totalSales: Int,
    val cashCollected: Int,
    val moneyPending: Int,
    val totalExpenses: Int,
    val expectedCash: Int,
    val pendingDeliveryUnits: Int,
    val pendingContainers: Int,
    val negativeStock: Map<String, Int>,
)

@Serializable
private data class CompletedRouteDto(
    val routeName: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val summary: CloseSummaryDto,
    val trackId: String? = null,
    val trackPointCount: Int = 0,
    val sales: List<SaleRecordDto> = emptyList(),
    val auditEntries: List<AuditEntryDto> = emptyList(),
    val activeDurationMillis: Long = 0L,
    val countedCash: Int? = null,
    val countedStock: Map<String, Int> = emptyMap(),
    val expectedStock: Map<String, Int> = emptyMap(),
)

@Serializable
private data class PersistedStateDto(
    val schemaVersion: Int = 1,
    val activeRoute: ActiveRouteDto? = null,
    val stores: List<StoreDto> = emptyList(),
    val completedRoutes: List<CompletedRouteDto> = emptyList(),
    val outstandingAccounts: List<AccountDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
)

object CaudalStateCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(state: CaudalRestoredState): String = json.encodeToString(state.toDto())

    fun decode(value: String): CaudalRestoredState = json.decodeFromString<PersistedStateDto>(value).toDomain()
}

class CaudalStateStore(private val stateFile: File) {
    fun load(): CaudalRestoredState? = runCatching {
        if (!stateFile.exists()) return null
        CaudalStateCodec.decode(stateFile.readText(Charsets.UTF_8))
    }.getOrNull()

    fun save(state: CaudalRestoredState) {
        stateFile.parentFile?.mkdirs()
        val temporary = File(stateFile.parentFile, "${stateFile.name}.tmp")
        temporary.writeText(CaudalStateCodec.encode(state), Charsets.UTF_8)
        if (stateFile.exists() && !stateFile.delete()) return
        temporary.renameTo(stateFile)
    }
}

private fun CaudalRestoredState.toDto() = PersistedStateDto(
    activeRoute = activeRoute?.let { route ->
        ActiveRouteDto(route.name, route.startedAtEpochMillis, route.ledger.exportState().toDto())
    },
    stores = stores.allStores().map { store ->
        StoreDto(store.id, store.name, store.location.latitude, store.location.longitude, store.phone, store.archived)
    },
    completedRoutes = completedRoutes.map { record ->
        CompletedRouteDto(
            record.routeName,
            record.startedAtEpochMillis,
            record.finishedAtEpochMillis,
            record.summary.toDto(),
            record.trackId,
            record.trackPointCount,
            record.sales.map(SaleRecord::toDto),
            record.auditEntries.map(RouteAuditEntry::toDto),
            record.activeDurationMillis,
            record.countedCash,
            record.countedStock,
            record.expectedStock,
        )
    },
    outstandingAccounts = outstandingAccounts.map(StoreAccountState::toDto),
    products = products.allProducts().map(Product::toDto),
)

private fun RouteLedgerState.toDto() = LedgerDto(
    initialCash = initialCash,
    products = products.map(Product::toDto),
    stock = stock,
    emptyContainers = emptyContainers,
    accounts = accounts.map(StoreAccountState::toDto),
    cashOnHand = cashOnHand,
    totalSales = totalSales,
    cashCollected = cashCollected,
    totalExpenses = totalExpenses,
    expenses = expenses.map {
        ExpenseDto(
            it.category.name,
            it.amount,
            it.id,
            it.recordedAtEpochMillis,
            it.cancelledAtEpochMillis,
            it.cancellationReason,
        )
    },
    kennethTransfers = kennethTransfers.map { transfer ->
        KennethTransferDto(
            direction = transfer.direction.name,
            lines = transfer.lines.map { line ->
                TransferLineDto(
                    line.product.toDto(),
                    line.fullUnits,
                    line.emptyUnits,
                )
            },
            recordedAtEpochMillis = transfer.recordedAtEpochMillis,
        )
    },
    sales = sales.map(SaleRecord::toDto),
    auditEntries = auditEntries.map(RouteAuditEntry::toDto),
)

private fun PersistedStateDto.toDomain(): CaudalRestoredState {
    require(schemaVersion == 1) { "Versión de datos no compatible" }
    val restoredStores = stores.map { dto ->
        Store(
            id = dto.id,
            name = dto.name,
            location = com.example.caudalapp.domain.GeoPoint(dto.latitude, dto.longitude),
            phone = dto.phone,
            archived = dto.archived,
        )
    }
    return CaudalRestoredState(
        activeRoute = activeRoute?.let { dto ->
            ActiveRoute(dto.name, RouteLedger.restore(dto.ledger.toDomain()), dto.startedAtEpochMillis)
        },
        stores = StoreDirectory.restore(restoredStores),
        completedRoutes = completedRoutes.map { dto ->
            CompletedRouteRecord(
                dto.routeName,
                dto.startedAtEpochMillis,
                dto.finishedAtEpochMillis,
                dto.summary.toDomain(),
                dto.trackId,
                dto.trackPointCount,
                dto.sales.map(SaleRecordDto::toDomain),
                dto.auditEntries.map(AuditEntryDto::toDomain),
                dto.activeDurationMillis,
                dto.countedCash,
                dto.countedStock,
                dto.expectedStock,
            )
        },
        outstandingAccounts = outstandingAccounts.map(AccountDto::toDomain),
        products = ProductDirectory.restore(products.map(ProductDto::toDomain)),
    )
}

private fun LedgerDto.toDomain() = RouteLedgerState(
    initialCash = initialCash,
    products = products.map(ProductDto::toDomain),
    stock = stock,
    emptyContainers = emptyContainers,
    accounts = accounts.map(AccountDto::toDomain),
    cashOnHand = cashOnHand,
    totalSales = totalSales,
    cashCollected = cashCollected,
    totalExpenses = totalExpenses,
    expenses = expenses.map { expense ->
        RouteExpense(
            ExpenseCategory.valueOf(expense.category),
            expense.amount,
            expense.id.ifBlank { "legacy-expense-${expense.category}-${expense.amount}" },
            expense.recordedAtEpochMillis,
            expense.cancelledAtEpochMillis,
            expense.cancellationReason,
        )
    },
    kennethTransfers = kennethTransfers.map { transfer ->
        KennethTransfer(
            direction = TransferDirection.valueOf(transfer.direction),
            lines = transfer.lines.map { line ->
                TransferLine(
                    product = line.product.toDomain(),
                    fullUnits = line.fullUnits,
                    emptyUnits = line.emptyUnits,
                )
            },
            recordedAtEpochMillis = transfer.recordedAtEpochMillis,
        )
    },
    sales = sales.map(SaleRecordDto::toDomain),
    auditEntries = auditEntries.map(AuditEntryDto::toDomain),
)

private fun Product.toDto() = ProductDto(
    id,
    name,
    defaultSuggestedTotal,
    returnable,
    imageUri,
    archived,
)

private fun ProductDto.toDomain() = Product(
    id,
    name,
    defaultSuggestedTotal,
    returnable,
    imageUri,
    archived,
)

private fun StoreAccountState.toDto() = AccountDto(
    storeId,
    debts.map { DebtDto(it.originalAmount, it.remainingAmount, it.id) },
    pendingDeliveries,
    pendingContainers,
    pendingPayments,
)

private fun AccountDto.toDomain() = StoreAccountState(
    storeId,
    debts.map { Debt(it.originalAmount, it.remainingAmount, it.id.ifBlank { "legacy-${storeId}-${it.originalAmount}" }) },
    pendingDeliveries,
    pendingContainers,
    pendingPayments,
)

private fun RouteCloseSummary.toDto() = CloseSummaryDto(
    initialCash,
    totalSales,
    cashCollected,
    moneyPending,
    totalExpenses,
    expectedCash,
    pendingDeliveryUnits,
    pendingContainers,
    negativeStock,
)

private fun CloseSummaryDto.toDomain() = RouteCloseSummary(
    initialCash,
    totalSales,
    cashCollected,
    moneyPending,
    totalExpenses,
    expectedCash,
    pendingDeliveryUnits,
    pendingContainers,
    negativeStock,
)

private fun SaleLine.toDto() = SaleLineDto(
    product.toDto(),
    requested,
    delivered,
    total,
    emptyContainersReceived,
    containersSold,
)

private fun SaleLineDto.toDomain() = SaleLine(
    product.toDomain(),
    requested,
    delivered,
    total,
    emptyContainersReceived,
    containersSold,
)

private fun SaleRecord.toDto() = SaleRecordDto(
    id,
    storeId,
    lines.map(SaleLine::toDto),
    amountReceived,
    recordedAtEpochMillis,
    debtId,
    pendingDeliveriesAdded,
    pendingContainersAdded,
    pendingPaymentProductId,
    pendingPaymentAdded,
    cancelledAtEpochMillis,
    cancellationReason,
    location?.latitude,
    location?.longitude,
    customerName,
    customerPhone,
)

private fun SaleRecordDto.toDomain() = SaleRecord(
    id,
    storeId,
    lines.map(SaleLineDto::toDomain),
    amountReceived,
    recordedAtEpochMillis,
    debtId,
    pendingDeliveriesAdded,
    pendingContainersAdded,
    pendingPaymentProductId,
    pendingPaymentAdded,
    cancelledAtEpochMillis,
    cancellationReason,
    if (latitude != null && longitude != null) com.example.caudalapp.domain.GeoPoint(latitude, longitude) else null,
    customerName,
    customerPhone,
)

private fun RouteAuditEntry.toDto() = AuditEntryDto(
    id,
    type.name,
    title,
    detail,
    cashDelta,
    recordedAtEpochMillis,
    relatedSaleId,
    relatedExpenseId,
)

private fun AuditEntryDto.toDomain() = RouteAuditEntry(
    id,
    RouteAuditType.valueOf(type),
    title,
    detail,
    cashDelta,
    recordedAtEpochMillis,
    relatedSaleId,
    relatedExpenseId,
)
