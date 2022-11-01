package ru.delimobil.deli_dgis.data.polygon

import ru.delimobil.deli_dgis.alias.DGisPolygonsHoles
import ru.delimobil.deli_dgis.alias.DGisPolygonsOutlines
import ru.delimobil.deli_dgis.data.options.DGisFillOptions
import ru.delimobil.deli_dgis.data.options.DGisStrokeOptions

data class DGisPolygonData(
    val outlines: DGisPolygonsOutlines,
    val holes: DGisPolygonsHoles,
    val fill: DGisFillOptions?,
    val stroke: DGisStrokeOptions?
)