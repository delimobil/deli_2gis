package ru.delimobil.deli_dgis.extensions

import ru.delimobil.deli_dgis.data.DGisCoordinates
import ru.dgis.sdk.coordinates.GeoPoint

fun GeoPoint.toDGisCoordinates(): DGisCoordinates {
    return DGisCoordinates(
        lat = latitude.value,
        lon = longitude.value
    )
}