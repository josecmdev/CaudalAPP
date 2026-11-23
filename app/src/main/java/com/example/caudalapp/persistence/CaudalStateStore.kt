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
import com.example.caudalapp.domain.StoreAccountAdjustment
import com.example.caudalapp.domain.StoreAccountAdjustmentType
import com.example.caudalapp.domain.KennethTransfer
import com.example.caudalapp.domain.TransferDirection
import com.example.caudalapp.domain.TransferLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class CaudalRestoredState(
    val activeRoute: ActiveRoute?,
    val stores: StoreDirectory,
    val completedRoutes: List<CompletedRouteRecord>,
    val outstandingAccounts: List<StoreAccountState> = emptyList(),
    val products: ProductDirectory = ProductDirectory(),
    val accountAdjustments: List<StoreAccountAdjustment> = emptyList(),
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
private data class AccountAdjustmentDto(
    val id: String,
    val storeId: String,
    val type: String,
    val amount: Int = 0,
    val productId: String? = null,
    val quantity: Int = 0,
    val recordedAtEpochMillis: Long,
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
    val archived: Boolean = false,
)

@Serializable
private data class PersistedStateDto(
    val schemaVersion: Int = 1,
    val activeRoute: ActiveRouteDto? = null,
    val stores: List<StoreDto> = emptyList(),
    val completedRoutes: List<CompletedRouteDto> = emptyList(),
    val outstandingAccounts: List<AccountDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val accountAdjustments: List<AccountAdjustmentDto> = emptyList(),
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

/**
 * Guarda todo el estado en un único archivo JSON.
 *
 * Reglas de seguridad:
 * - Si el archivo existe pero no se puede leer o decodificar, se aparta con el sufijo
 *   `.unreadable-<epochMillis>` en lugar de ignorarse, para que el siguiente guardado no
 *   sobrescriba los datos originales. [lastSetAsideFile] indica dónde quedó.
 * - El guardado escribe primero un archivo temporal sincronizado con el disco y luego lo
 *   reemplaza con un rename atómico, así el archivo principal nunca queda a medio escribir
 *   ni desaparece.
 */
class CaudalStateStore(
    private val stateFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val directory: File get() = stateFile.absoluteFile.parentFile
    /** El historial va aparte: casi nunca cambia y es lo que más pesa, así cada venta escribe un archivo chico. */
    private val historyFile: File get() = File(directory, "${stateFile.nameWithoutExtension}-history.json")

    /** Lo que hay en [historyFile] según este guardador; null si no lo sabe. */
    private var savedHistory: List<CompletedRouteRecord>? = null

    /** Archivo ilegible que la última llamada a [load] apartó, o null si no apartó ninguno. */
    var lastSetAsideFile: File? = null
        private set

    fun load(): CaudalRestoredState? {
        lastSetAsideFile = null
        recoverInterruptedFallbackSave(stateFile)
        recoverInterruptedFallbackSave(historyFile)
        if (!stateFile.exists()) return null
        val state = runCatching { CaudalStateCodec.decode(stateFile.readText(Charsets.UTF_8)) }
            .getOrElse {
                lastSetAsideFile = setAsideUnreadableFile(stateFile)
                return null
            }
        // Sin archivo de historial es el formato anterior: el historial viene dentro del principal.
        if (!historyFile.exists()) return state
        val history = runCatching { CaudalStateCodec.decode(historyFile.readText(Charsets.UTF_8)).completedRoutes }
            .getOrElse {
                lastSetAsideFile = setAsideUnreadableFile(historyFile)
                return state
            }
        savedHistory = history
        return state.copy(completedRoutes = history)
    }

    fun save(state: CaudalRestoredState) {
        directory.mkdirs()
        val history = state.completedRoutes
        val historyChanged = if (historyFile.exists()) history != savedHistory else history.isNotEmpty()
        // Primero el historial: si se corta en medio, queda historial nuevo con principal viejo, sin perder rutas.
        if (historyChanged) {
            writeSafely(historyFile, CaudalStateCodec.encode(CaudalRestoredState(null, StoreDirectory(), history)))
            savedHistory = history
        }
        writeSafely(stateFile, CaudalStateCodec.encode(state.copy(completedRoutes = emptyList())))
    }

    private fun writeSafely(target: File, text: String) {
        val temporary = File(directory, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        // En Android y en la JVM sobre Linux, renameTo es rename(2): atómico y reemplaza el destino.
        if (temporary.renameTo(target)) return
        replaceWithoutAtomicRename(target, temporary)
    }

    /**
     * Respaldo para sistemas donde rename no reemplaza un archivo existente (por ejemplo Windows
     * al correr pruebas). Mueve el archivo anterior a `.previous` antes de colocar el nuevo, de
     * modo que en ningún momento se pierden ambos.
     */
    private fun replaceWithoutAtomicRename(target: File, temporary: File) {
        val previous = File(directory, "${target.name}.previous")
        previous.delete()
        if (target.exists() && !target.renameTo(previous)) {
            throw IOException("No se pudo reemplazar ${target.name}")
        }
        if (!temporary.renameTo(target)) {
            previous.renameTo(target)
            throw IOException("No se pudo guardar ${target.name}")
        }
        previous.delete()
    }

    /** Si un guardado de respaldo se interrumpió entre sus dos renames, restaura el archivo anterior. */
    private fun recoverInterruptedFallbackSave(file: File) {
        val previous = File(directory, "${file.name}.previous")
        if (!file.exists() && previous.exists()) previous.renameTo(file)
    }

    private fun setAsideUnreadableFile(file: File): File? {
        val target = File(directory, "${file.name}.unreadable-${clock()}")
        if (file.renameTo(target)) return target
        // Si no se puede mover, se copia; si tampoco, se deja el original intacto y se avisa.
        return runCatching {
            file.copyTo(target, overwrite = false)
            file.delete()
            target
        }.getOrNull()
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
            record.archived,
        )
    },
    outstandingAccounts = outstandingAccounts.map(StoreAccountState::toDto),
    products = products.allProducts().map(Product::toDto),
    accountAdjustments = accountAdjustments.map { adjustment ->
        AccountAdjustmentDto(
            adjustment.id,
            adjustment.storeId,
            adjustment.type.name,
            adjustment.amount,
            adjustment.productId,
            adjustment.quantity,
            adjustment.recordedAtEpochMillis,
        )
    },
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
                dto.archived,
            )
        },
        outstandingAccounts = outstandingAccounts.map(AccountDto::toDomain),
        products = ProductDirectory.restore(products.map(ProductDto::toDomain)),
        accountAdjustments = accountAdjustments.map { adjustment ->
            StoreAccountAdjustment(
                adjustment.id,
                adjustment.storeId,
                StoreAccountAdjustmentType.valueOf(adjustment.type),
                adjustment.amount,
                adjustment.productId,
                adjustment.quantity,
                adjustment.recordedAtEpochMillis,
            )
        },
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
