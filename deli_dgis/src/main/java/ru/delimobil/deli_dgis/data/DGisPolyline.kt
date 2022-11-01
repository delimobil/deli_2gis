package ru.delimobil.deli_dgis.data

data class DGisPolyline(
    val groupId: String,
    val options: Options
) {

    data class Options(
        val points: List<DGisCoordinates>,
        val color: Int,
        val width: Float
    )
}