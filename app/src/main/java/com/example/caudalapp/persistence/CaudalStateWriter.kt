package com.example.caudalapp.persistence

import com.example.caudalapp.domain.ActiveRoute
import com.example.caudalapp.domain.ProductDirectory
import com.example.caudalapp.domain.RouteLedger
import com.example.caudalapp.domain.StoreDirectory
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Único hilo que toca el archivo de estado, compartido por la tablet y Android Auto (mismo proceso).
 * Al ir todo en fila, un guardado viejo nunca pisa uno nuevo y el carro no lee a medio escribir.
 */
object CaudalStateWriter {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "caudal-state-writer").apply { isDaemon = true }
    }
    private val externalChangeListeners = CopyOnWriteArraySet<() -> Unit>()

    fun enqueue(task: () -> Unit) {
        executor.execute { runCatching(task).onFailure(Throwable::printStackTrace) }
    }

    /** Espera a que terminen los guardados en fila y luego corre [task]. */
    fun <T> runBlocking(task: () -> T): T = executor.submit(Callable(task)).get()

    fun addExternalChangeListener(listener: () -> Unit) {
        externalChangeListeners += listener
    }

    fun removeExternalChangeListener(listener: () -> Unit) {
        externalChangeListeners -= listener
    }

    /** Avisa a la tablet que alguien más (el carro) cambió el archivo. */
    fun notifyExternalChange() = externalChangeListeners.forEach { listener ->
        runCatching(listener).onFailure(Throwable::printStackTrace)
    }
}

/** Copia que el hilo de guardado puede leer sin chocar con la pantalla, que sigue modificando el original. */
fun CaudalRestoredState.detachedCopy(): CaudalRestoredState = copy(
    activeRoute = activeRoute?.let { route ->
        ActiveRoute(route.name, RouteLedger.restore(route.ledger.exportState()), route.startedAtEpochMillis)
    },
    stores = StoreDirectory.restore(stores.allStores()),
    completedRoutes = completedRoutes.toList(),
    outstandingAccounts = outstandingAccounts.toList(),
    products = ProductDirectory.restore(products.allProducts()),
    accountAdjustments = accountAdjustments.toList(),
)
