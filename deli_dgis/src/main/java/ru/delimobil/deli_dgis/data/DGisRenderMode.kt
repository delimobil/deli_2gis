package ru.delimobil.deli_dgis.data

sealed class DGisRenderMode {
    object Texture : DGisRenderMode()
    object Surface : DGisRenderMode()
}