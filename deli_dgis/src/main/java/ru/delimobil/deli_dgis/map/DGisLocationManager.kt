package ru.delimobil.deli_dgis.map

import android.content.Context
import ru.delimobil.deli_dgis.extensions.hasLocationPermission
import ru.delimobil.deli_location_provider.DeliLocationProvider
import ru.delimobil.deli_location_provider.data.DeliLocationResult
import ru.dgis.sdk.positioning.DesiredAccuracy
import ru.dgis.sdk.positioning.LocationChangeListener
import ru.dgis.sdk.positioning.LocationSource


class DGisLocationManager(
    private val context: Context,
    private val deliLocationProvider: DeliLocationProvider
) : LocationSource {

    companion object {
        const val LOCATION_REQUEST_INTERVAL = 3000L
    }

    private var sdkListener: LocationChangeListener? = null

    private fun listenLocation() {
        val listener = sdkListener ?: return
        if (!context.hasLocationPermission()) return
        deliLocationProvider.stopListenLocation()
        deliLocationProvider.startListenLocation(
            updateInterval = LOCATION_REQUEST_INTERVAL
        ) { result ->
            when (result) {
                is DeliLocationResult.Success -> {
                    listener.onAvailabilityChanged(true)
                    listener.onLocationChanged(arrayOf(result.location))
                }
                else -> {
                    listener.onAvailabilityChanged(false)
                }
            }
        }
    }

    override fun activate(listener: LocationChangeListener) {
        sdkListener = listener
        listenLocation()
    }

    override fun deactivate() {
        sdkListener = null
        deliLocationProvider.stopListenLocation()
    }

    override fun setDesiredAccuracy(accuracy: DesiredAccuracy) {
        listenLocation()
    }
}