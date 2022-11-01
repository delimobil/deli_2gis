package ru.delimobil.deli_dgis.data.options

data class DGisStrokeOptions(
    val width: Float,
    val color: Int,
    val pattern: Pattern? = null
) {

    data class Pattern(
        val gap: Float,
        val dash: Float
    )
}