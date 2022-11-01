package ru.delimobil.deli_dgis.data

import kotlin.math.max
import kotlin.math.min

data class DGisLatLngBounds(
    val northLatitude: Double,
    val eastLongitude: Double,
    val southLatitude: Double,
    val westLongitude: Double
) {

    companion object {
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -Double.MAX_VALUE
        private const val MAX_LONGITUDE = Double.MAX_VALUE
        private const val MIN_LATITUDE = -90.0

        fun fromCoordinates(coordinates: List<DGisCoordinates>): DGisLatLngBounds {
            var minLat = MAX_LATITUDE
            var minLon = MAX_LONGITUDE
            var maxLat = MIN_LATITUDE
            var maxLon = MIN_LONGITUDE
            coordinates.forEach {
                val latitude = it.lat
                val longitude = it.lon
                minLat = min(minLat, latitude)
                minLon = min(minLon, longitude)
                maxLat = max(maxLat, latitude)
                maxLon = max(maxLon, longitude)
            }
            return DGisLatLngBounds(maxLat, maxLon, minLat, minLon)
        }
    }

    fun getCenter(): DGisCoordinates {
        val latCenter = (this.northLatitude + this.southLatitude) / 2.0
        val longCenter = (this.eastLongitude + this.westLongitude) / 2.0
        return DGisCoordinates(latCenter, longCenter)
    }

    fun getSouthWest(): DGisCoordinates {
        return DGisCoordinates(southLatitude, westLongitude)
    }

    fun getNorthEast(): DGisCoordinates {
        return DGisCoordinates(northLatitude, eastLongitude)
    }

    fun getSouthEast(): DGisCoordinates {
        return DGisCoordinates(southLatitude, eastLongitude)
    }

    fun getNorthWest(): DGisCoordinates {
        return DGisCoordinates(northLatitude, westLongitude)
    }
}