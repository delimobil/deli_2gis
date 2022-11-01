package ru.delimobil.deli_location_provider.data

import android.location.Location

sealed class DeliLocationResult {
    object PermissionRequired : DeliLocationResult()

    object ProviderNotFound : DeliLocationResult()

    object NoData : DeliLocationResult()

    data class Success(
        val location: Location
    ) : DeliLocationResult()
}