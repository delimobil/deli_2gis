package ru.delimobil.deli_location_provider.helper

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.os.Looper
import ru.delimobil.deli_location_provider.SystemLocationServices
import ru.delimobil.deli_location_provider.data.DeliLocationResult

class SystemLocationHelper(
    private val systemLocationServices: SystemLocationServices
) : LocationHelper {

    private val locationUpdateCallback by lazy {
        SimpleLocationCallback { location, _ ->
            onLocationUpdate?.invoke(
                location
                    ?.let { DeliLocationResult.Success(it) }
                    ?: DeliLocationResult.NoData
            )
        }
    }

    private var onLocationUpdate: ((DeliLocationResult) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override fun getLastLocation(result: (DeliLocationResult) -> Unit) {
        result.invoke(
            systemLocationServices.locationManager
                ?.getLastKnownLocation(systemLocationServices.locationProvider.orEmpty())
                ?.let { DeliLocationResult.Success(it) }
                ?: DeliLocationResult.NoData
        )
    }

    @SuppressLint("MissingPermission")
    override fun getActualLocation(result: (DeliLocationResult) -> Unit) {
        systemLocationServices.locationManager?.requestLocationUpdates(
            systemLocationServices.locationProvider.orEmpty(),
            0,
            0f,
            SimpleLocationCallback { location, callback ->
                result.invoke(
                    location
                        ?.let { DeliLocationResult.Success(it) }
                        ?: DeliLocationResult.NoData
                )

                systemLocationServices.locationManager?.removeUpdates(callback)
            }
        )
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(result: (DeliLocationResult) -> Unit, interval: Long) {
        onLocationUpdate = result
        systemLocationServices.locationManager?.requestLocationUpdates(
            systemLocationServices.locationProvider.orEmpty(),
            interval,
            0f,
            locationUpdateCallback,
            Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    override fun stopLocationUpdates() {
        onLocationUpdate = null
        systemLocationServices.locationManager?.removeUpdates(locationUpdateCallback)
    }

    private inner class SimpleLocationCallback(
        private val onLocationResult: (Location?, LocationListener) -> Unit
    ) : LocationListener {

        override fun onLocationChanged(location: Location) {
            onLocationResult.invoke(location, this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // do nothing
        }

        override fun onProviderEnabled(provider: String) {
            // do nothing
        }

        override fun onProviderDisabled(provider: String) {
            // do nothing
        }

    }
}