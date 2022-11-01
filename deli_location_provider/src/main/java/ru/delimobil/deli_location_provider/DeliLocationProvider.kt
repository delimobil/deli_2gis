package ru.delimobil.deli_location_provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ru.delimobil.deli_location_provider.data.DeliLocationResult
import ru.delimobil.deli_location_provider.helper.LocationHelper

abstract class DeliLocationProvider(
    private val context: Context
) {

    protected val systemLocationServices by lazy {
        SystemLocationServices(context)
    }

    abstract val vendorLocationHelper: LocationHelper

    private val hasPermissions: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED


    private fun resolveLocationService(
        result: (DeliLocationResult) -> Unit,
        onServiceAvailable: () -> Unit
    ) {
        when {
            !hasPermissions -> result.invoke(DeliLocationResult.PermissionRequired)
            !systemLocationServices.hasLocationProvider -> result.invoke(DeliLocationResult.ProviderNotFound)
            else -> onServiceAvailable.invoke()
        }
    }

    fun getLastLocation(result: (DeliLocationResult) -> Unit) = resolveLocationService(
        result = result,
        onServiceAvailable = { vendorLocationHelper.getLastLocation(result) }
    )

    fun getActualLocation(result: (DeliLocationResult) -> Unit) = resolveLocationService(
        result = result,
        onServiceAvailable = { vendorLocationHelper.getActualLocation(result) }
    )

    fun startListenLocation(
        updateInterval: Long = DEFAULT_UPDATE_INTERVAL,
        result: (DeliLocationResult) -> Unit
    ) = resolveLocationService(
        result = result,
        onServiceAvailable = {
            vendorLocationHelper.startLocationUpdates(
                result = result,
                interval = updateInterval
            )
        }
    )

    fun stopListenLocation() {
        if (hasPermissions) vendorLocationHelper.stopLocationUpdates()
    }

    private companion object {
        const val DEFAULT_UPDATE_INTERVAL = 5000L
    }
}