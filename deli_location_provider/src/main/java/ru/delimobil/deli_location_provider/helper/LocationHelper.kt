package ru.delimobil.deli_location_provider.helper

import ru.delimobil.deli_location_provider.data.DeliLocationResult

interface LocationHelper {
    fun getLastLocation(result: (DeliLocationResult) -> Unit)
    fun getActualLocation(result: (DeliLocationResult) -> Unit)
    fun startLocationUpdates(result: (DeliLocationResult) -> Unit, interval: Long)
    fun stopLocationUpdates()
}