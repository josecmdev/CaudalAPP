package com.example.caudalapp

import android.content.Context
import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.OfflineAreaPolicy
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class OfflineMapStatus(
    val regionCount: Int = 0,
    val downloading: Boolean = false,
    val progressPercent: Int = 0,
    val downloadedMegabytes: Double = 0.0,
    val message: String? = null,
)

class CaudalOfflineMaps(context: Context) {
    private val appContext = context.applicationContext
    private val manager: OfflineManager

    init {
        MapLibre.getInstance(appContext)
        manager = OfflineManager.getInstance(appContext)
    }

    fun refresh(onStatus: (OfflineMapStatus) -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions ?: emptyArray()
                if (regions.isEmpty()) {
                    onStatus(OfflineMapStatus())
                    return
                }
                var completedResources = 0L
                var completedBytes = 0L
                var pending = regions.size
                regions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status == null) {
                                pending--
                                if (pending == 0) onStatus(OfflineMapStatus(regionCount = regions.size))
                                return
                            }
                            completedResources += status.completedResourceCount
                            completedBytes += status.completedResourceSize
                            pending--
                            if (pending == 0) {
                                onStatus(
                                    OfflineMapStatus(
                                        regionCount = regions.size,
                                        downloadedMegabytes = completedBytes / 1_048_576.0,
                                        message = "$completedResources recursos disponibles sin internet",
                                    ),
                                )
                            }
                        }

                        override fun onError(error: String?) {
                            pending--
                            if (pending == 0) onStatus(OfflineMapStatus(regionCount = regions.size, message = error))
                        }
                    })
                }
            }

            override fun onError(error: String) {
                onStatus(OfflineMapStatus(message = error))
            }
        })
    }

    fun download(center: GeoPoint, radiusKm: Int, onStatus: (OfflineMapStatus) -> Unit) {
        val area = OfflineAreaPolicy.boundsAround(center, radiusKm)
        val bounds = LatLngBounds.Builder()
            .include(LatLng(area.southWest.latitude, area.southWest.longitude))
            .include(LatLng(area.northEast.latitude, area.northEast.longitude))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            OFFLINE_STYLE,
            bounds,
            MIN_OFFLINE_ZOOM,
            MAX_OFFLINE_ZOOM,
            appContext.resources.displayMetrics.density,
        )
        val metadata = "Caudal|${center.latitude}|${center.longitude}|$radiusKm".toByteArray(Charsets.UTF_8)
        onStatus(OfflineMapStatus(downloading = true, message = "Preparando descarga…"))
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val required = status.requiredResourceCount
                        val progress = if (required > 0L) {
                            ((status.completedResourceCount * 100L) / required).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        onStatus(
                            OfflineMapStatus(
                                regionCount = 1,
                                downloading = !status.isComplete,
                                progressPercent = progress,
                                downloadedMegabytes = status.completedResourceSize / 1_048_576.0,
                                message = if (status.isComplete) "Mapa listo para usar sin internet" else "Descargando mapa…",
                            ),
                        )
                        if (status.isComplete) offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    }

                    override fun onError(error: OfflineRegionError) {
                        onStatus(OfflineMapStatus(message = "${error.reason}: ${error.message}"))
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        onStatus(OfflineMapStatus(message = "La zona supera el límite de $limit mosaicos"))
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                onStatus(OfflineMapStatus(message = error))
            }
        })
    }

    fun deleteAll(onComplete: (Boolean) -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions ?: emptyArray()
                if (regions.isEmpty()) {
                    onComplete(true)
                    return
                }
                var pending = regions.size
                var successful = true
                regions.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            pending--
                            if (pending == 0) onComplete(successful)
                        }

                        override fun onError(error: String) {
                            successful = false
                            pending--
                            if (pending == 0) onComplete(false)
                        }
                    })
                }
            }

            override fun onError(error: String) = onComplete(false)
        })
    }

    companion object {
        private const val OFFLINE_STYLE = "https://tiles.openfreemap.org/styles/bright"
        private const val MIN_OFFLINE_ZOOM = 9.0
        private const val MAX_OFFLINE_ZOOM = 17.0
    }
}
