package com.example.caudalapp.domain

import java.util.UUID

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    companion object {
        fun unchecked(latitude: Double, longitude: Double) = GeoPoint(latitude, longitude)
    }
}

data class Store(
    val id: String,
    var name: String,
    var location: GeoPoint,
    var phone: String? = null,
    var archived: Boolean = false,
)

sealed interface StoreChangeResult {
    data class Success(val store: Store) : StoreChangeResult
    data class Rejected(val errors: List<String>) : StoreChangeResult
}

data class StoreObligations(
    val moneyDue: Int = 0,
    val containersDue: Int = 0,
    val unitsPending: Int = 0,
) {
    val hasAny: Boolean
        get() = moneyDue > 0 || containersDue > 0 || unitsPending > 0
}

data class ArchiveStoreResult(
    val archived: Boolean,
    val hadPendingObligations: Boolean,
)

class StoreDirectory(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val stores = linkedMapOf<String, Store>()

    fun addStore(name: String, location: GeoPoint, phone: String? = null): StoreChangeResult {
        val errors = buildList {
            if (name.isBlank()) add("Escribe el nombre de la tienda")
            if (!location.isValid) add("La ubicación seleccionada no es válida")
        }
        if (errors.isNotEmpty()) return StoreChangeResult.Rejected(errors)

        val store = Store(
            id = idGenerator(),
            name = name.trim(),
            location = location,
            phone = phone?.trim()?.takeIf(String::isNotEmpty),
        )
        stores[store.id] = store
        return StoreChangeResult.Success(store)
    }

    fun moveStore(storeId: String, newLocation: GeoPoint): StoreChangeResult {
        val store = stores[storeId]
            ?: return StoreChangeResult.Rejected(listOf("La tienda ya no existe"))
        if (!newLocation.isValid) {
            return StoreChangeResult.Rejected(listOf("La ubicación seleccionada no es válida"))
        }
        store.location = newLocation
        return StoreChangeResult.Success(store)
    }

    fun updateStore(storeId: String, name: String, phone: String?): StoreChangeResult {
        val store = stores[storeId]
            ?: return StoreChangeResult.Rejected(listOf("La tienda ya no existe"))
        if (name.isBlank()) {
            return StoreChangeResult.Rejected(listOf("Escribe el nombre de la tienda"))
        }
        store.name = name.trim()
        store.phone = phone?.trim()?.takeIf(String::isNotEmpty)
        return StoreChangeResult.Success(store)
    }

    fun archiveStore(
        storeId: String,
        obligations: StoreObligations = StoreObligations(),
        confirmed: Boolean,
    ): ArchiveStoreResult {
        val store = stores[storeId]
            ?: return ArchiveStoreResult(archived = false, hadPendingObligations = false)
        if (obligations.hasAny && !confirmed) {
            return ArchiveStoreResult(archived = false, hadPendingObligations = true)
        }
        store.archived = true
        return ArchiveStoreResult(archived = true, hadPendingObligations = obligations.hasAny)
    }

    fun get(storeId: String): Store? = stores[storeId]

    fun restoreStore(storeId: String): Boolean {
        val store = stores[storeId] ?: return false
        store.archived = false
        return true
    }

    fun activeStores(): List<Store> = stores.values.filterNot(Store::archived)

    fun allStores(): List<Store> = stores.values.toList()

    companion object {
        fun restore(savedStores: List<Store>): StoreDirectory = StoreDirectory().apply {
            savedStores.forEach { store -> stores[store.id] = store.copy() }
        }
    }
}
