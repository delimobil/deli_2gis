package ru.delimobil.deli_dgis.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DGisRegion(
    val southWest: DGisCoordinates,
    val northEast: DGisCoordinates,
    val center: DGisCoordinates
) {

    companion object {
        fun fromCoordinates(coordinates: List<DGisCoordinates>, center: DGisCoordinates): DGisRegion {
            val bounds = DGisLatLngBounds.fromCoordinates(coordinates)
            return DGisRegion(
                northEast = DGisCoordinates(bounds.northLatitude, bounds.eastLongitude),
                southWest = DGisCoordinates(bounds.southLatitude, bounds.westLongitude),
                center = center
            )
        }
    }

    fun asEffectiveRegionForMarkers(): DGisRegion {
        val verticalOffset = (abs(max(northEast.lat, southWest.lat)) - abs(min(northEast.lat, southWest.lat))) / 4
        val horizontalOffset = (abs(max(northEast.lon, southWest.lon)) - abs(min(northEast.lon, southWest.lon))) / 2

        return DGisRegion(
            southWest = DGisCoordinates(
                lat = southWest.lat - verticalOffset,
                lon = southWest.lon - horizontalOffset
            ),
            northEast = DGisCoordinates(
                lat = northEast.lat + verticalOffset,
                lon = northEast.lon + horizontalOffset
            ),
            center = center
        )
    }

    fun contains(coordinates: DGisCoordinates): Boolean {
        return containLat(coordinates.lat) && containLng(coordinates.lon)
    }

    private fun containLat(lat: Double): Boolean {
        return if (southWest.lat <= northEast.lat) {
            southWest.lat <= lat && lat <= northEast.lat
        } else {
            northEast.lat <= lat && lat <= southWest.lat
        }
    }

    private fun containLng(lng: Double): Boolean {
        return if (southWest.lon <= northEast.lon) {
            southWest.lon <= lng && lng <= northEast.lon
        } else {
            northEast.lon <= lng && lng <= southWest.lon
        }
    }
}