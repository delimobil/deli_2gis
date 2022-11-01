package ru.delimobil.deli_dgis.data

import ru.delimobil.deli_dgis.data.options.DGisFillOptions
import ru.delimobil.deli_dgis.data.options.DGisStrokeOptions

data class DGisCircles(
    val groupId: String,
    val options: List<Options>
) {

    data class Options(
        val center: DGisCoordinates,
        val radius: Float,
        val stroke: DGisStrokeOptions? = null,
        val fill: DGisFillOptions
    )
}