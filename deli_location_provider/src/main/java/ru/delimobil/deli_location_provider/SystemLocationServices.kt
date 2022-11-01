package ru.delimobil.deli_location_provider

import android.content.Context
import android.location.LocationManager

class SystemLocationServices(
    private val context: Context
) {

    val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    val locationProvider: String?
        get() {
            return locationManager?.let { manager ->
                when {
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
            }
        }

    val hasLocationProvider: Boolean
        get() = locationProvider != null

}