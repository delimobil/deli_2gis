package ru.delimobil.deli_dgis.data.options

data class DGisCameraOptions(
    val animate: Boolean,
    val padding: Int = 0,
    val zoom: Float? = null,
    val tilt: Float? = null,
    val bearing: Double? = null
)