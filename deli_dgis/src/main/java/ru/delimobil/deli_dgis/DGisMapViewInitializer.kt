package ru.delimobil.deli_dgis

import androidx.lifecycle.Lifecycle
import ru.delimobil.deli_dgis.alias.DGisContext
import ru.delimobil.deli_dgis.alias.DGisFile
import ru.delimobil.deli_dgis.data.DGisRenderMode
import ru.delimobil.deli_dgis.data.DGisSource
import ru.delimobil.deli_location_provider.DeliLocationProvider
import java.util.*

class DGisMapViewInitializer(
    private val dGisContext: DGisContext,
    private val deliLocationProvider: DeliLocationProvider,
    private val style: DGisFile? = null,
    private val sources: List<DGisSource>
) {
    fun initView(
        mapView: DGisMapView,
        lifecycle: Lifecycle,
        locale: Locale,
        isClickable: Boolean = true,
        renderMode: DGisRenderMode = DGisRenderMode.Texture,
        onMapReady: (DGisMapHelper) -> Unit
    ) {
        mapView.initialize(
            lifecycle = lifecycle,
            dGisContext = dGisContext,
            deliLocationProvider = deliLocationProvider,
            style = style,
            isClickable = isClickable,
            locales = listOf(locale.language),
            sources = sources,
            renderMode = renderMode,
            onMapReady = onMapReady
        )
    }
}