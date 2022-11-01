package ru.delimobil.deli_dgis.extensions

import ru.delimobil.deli_dgis.data.DGisCoordinates
import ru.dgis.sdk.coordinates.GeoPoint
import kotlin.math.*

fun DGisCoordinates.toGeoPoint(): GeoPoint {
    return GeoPoint(lat, lon)
}

fun List<DGisCoordinates>.mapToGeoPointsList(): List<GeoPoint>? {
    return takeIf { outlines -> outlines.firstOrNull() == outlines.lastOrNull() && outlines.size >= 4 }
        ?.map { outlines ->
            GeoPoint(outlines.lat, outlines.lon)
        }
}

fun DGisCoordinates.approxDistanceTo(other: DGisCoordinates): Double {
    val deltaLat = other.lat - lat
    val deltaLon = other.lon - lon
    val angle = (2 * asin(
        sqrt(
            sin(deltaLat / 2)
                .pow(2.0) + cos(lat) * cos(other.lat) * sin(deltaLon / 2)
                .pow(2.0)
        )
    ))
    return 6378137 * angle
}