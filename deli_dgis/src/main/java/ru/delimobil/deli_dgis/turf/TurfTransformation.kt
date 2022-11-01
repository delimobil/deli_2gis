package ru.delimobil.deli_dgis.turf

import androidx.annotation.FloatRange
import ru.delimobil.deli_dgis.turf.TurfConversion.degreesToRadians
import ru.delimobil.deli_dgis.turf.TurfConversion.lengthToRadians
import ru.delimobil.deli_dgis.turf.TurfConversion.radiansToDegrees
import ru.dgis.sdk.coordinates.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object TurfTransformation {

    private const val DEFAULT_STEPS = 64

    fun circle(
        center: GeoPoint, radius: Double,
        unit: TurfUnit
    ): List<GeoPoint> {
        val coordinates: MutableList<GeoPoint> = ArrayList()
        for (i in 0 until DEFAULT_STEPS) {
            coordinates.add(destination(center, radius, i * 360.0 / DEFAULT_STEPS, unit))
        }
        if (coordinates.size > 0) {
            coordinates.add(coordinates[0])
        }
        return coordinates
    }

    @JvmStatic
    private fun destination(
        geoPoint: GeoPoint,
        @FloatRange(from = 0.0) distance: Double,
        @FloatRange(from = -180.0, to = 180.0) bearing: Double,
        units: TurfUnit
    ): GeoPoint {
        val longitude1 = degreesToRadians(geoPoint.longitude.value)
        val latitude1 = degreesToRadians(geoPoint.latitude.value)
        val bearingRad = degreesToRadians(bearing)
        val radians = lengthToRadians(distance, units)
        val latitude2 = asin(
            sin(latitude1) * cos(radians)
                    + cos(latitude1) * sin(radians) * cos(bearingRad)
        )
        val longitude2 = longitude1 + atan2(
            y = sin(bearingRad) * sin(radians) * cos(latitude1),
            x = cos(radians) - sin(latitude1) * sin(latitude2)
        )
        return GeoPoint(
            latitude = radiansToDegrees(latitude2),
            longitude = radiansToDegrees(longitude2)
        )
    }
}