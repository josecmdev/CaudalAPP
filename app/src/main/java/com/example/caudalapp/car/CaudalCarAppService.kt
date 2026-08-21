package com.example.caudalapp.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.caudalapp.RouteTrackingService
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import java.io.File

class CaudalCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = CaudalCarSession()
}

private class CaudalCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val repository = CaudalCarRepository(File(carContext.filesDir, "caudal-state-v1.json"))
        return CarStoresScreen(carContext, repository)
    }
}

@Suppress("DEPRECATION")
private class CarStoresScreen(
    carContext: CarContext,
    private val repository: CaudalCarRepository,
) : Screen(carContext) {
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            invalidate()
            clockHandler.postDelayed(this, 1_000L)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                clockHandler.removeCallbacks(clockTick)
                clockHandler.post(clockTick)
            }

            override fun onStop(owner: LifecycleOwner) {
                clockHandler.removeCallbacks(clockTick)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                clockHandler.removeCallbacks(clockTick)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val snapshot = repository.snapshot()
        if (snapshot.stores.isEmpty()) {
            return MessageTemplate.Builder("Agrega tiendas desde Caudal App en este dispositivo.")
                .setTitle("Caudal Auto")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val stores = ItemList.Builder()
        snapshot.stores.forEach { store ->
            val status = buildList {
                if (store.moneyDue > 0) add("Debe Q${store.moneyDue}")
                if (store.containersDue > 0) add("${store.containersDue} envases")
                if (store.unitsPending > 0) add("${store.unitsPending} por entregar")
            }.ifEmpty { listOf("Sin pendientes") }.joinToString(" · ")
            val place = Place.Builder(CarLocation.create(store.latitude, store.longitude))
                .setMarker(
                    PlaceMarker.Builder()
                        .setLabel(store.name.markerLabel())
                        .setColor(
                            when {
                                store.moneyDue > 0 -> CarColor.RED
                                store.containersDue > 0 || store.unitsPending > 0 -> CarColor.YELLOW
                                else -> CarColor.GREEN
                            },
                        )
                        .build(),
                )
                .build()
            stores.addItem(
                Row.Builder()
                    .setTitle(store.name)
                    .addText(status)
                    .setBrowsable(true)
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setOnClickListener {
                        screenManager.push(CarStoreActionsScreen(carContext, repository, store.id))
                    }
                    .build(),
            )
        }
        val subtitle = snapshot.routeName?.let { routeName ->
            val elapsed = snapshot.routeStartedAtEpochMillis?.let { startedAt ->
                RouteTrackingService.elapsedMillis(carContext, RouteTrackingService.routeId(startedAt))
            } ?: 0L
            "$routeName · ${formatCarDuration(elapsed)} · ${snapshot.bagStock} bolsas"
        } ?: "Sin ruta activa"
        return PlaceListMapTemplate.Builder()
            .setTitle("Caudal Auto · $subtitle")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build(),
            )
            .setItemList(stores.build())
            .build()
    }

    private fun String.markerLabel(): String =
        trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .take(2)
            .ifBlank { "T" }

    private fun formatCarDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

private class CarStoreActionsScreen(
    carContext: CarContext,
    private val repository: CaudalCarRepository,
    private val storeId: String,
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val snapshot = repository.snapshot()
        val store = snapshot.stores.firstOrNull { it.id == storeId }
            ?: return MessageTemplate.Builder("La tienda ya no está disponible.")
                .setTitle("Caudal Auto")
                .setHeaderAction(Action.BACK)
                .build()

        val rows = ItemList.Builder()
        rows.addItem(
            Row.Builder()
                .setTitle(store.name)
                .addText(snapshot.routeName ?: "No hay una ruta activa")
                .addText("Inventario: ${snapshot.bagStock} bolsas")
                .build(),
        )
        if (store.moneyDue > 0) {
            rows.addItem(
                parkedOperationRow("Cobrar deuda completa", "Registrar Q${store.moneyDue}") {
                    perform(repository.collectFullDebt(store.id))
                },
            )
        }
        CarBagPresetPolicy.quantities.forEach { quantity ->
            val total = requireNotNull(CarBagPresetPolicy.totalFor(quantity))
            rows.addItem(
                parkedOperationRow("Vender $quantity bolsas", "Contado · Q$total") {
                    perform(repository.sellBags(store.id, quantity))
                },
            )
        }
        return ListTemplate.Builder()
            .setTitle(store.name)
            .setHeaderAction(Action.BACK)
            .setSingleList(rows.build())
            .build()
    }

    private fun parkedOperationRow(title: String, detail: String, action: () -> Unit): Row =
        Row.Builder()
            .setTitle(title)
            .addText(detail)
            .setOnClickListener(ParkedOnlyOnClickListener.create(action))
            .build()

    private fun perform(result: CarOperationResult) {
        val message = when (result) {
            is CarOperationResult.Success -> result.message
            is CarOperationResult.Rejected -> result.message
        }
        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
        invalidate()
    }
}
