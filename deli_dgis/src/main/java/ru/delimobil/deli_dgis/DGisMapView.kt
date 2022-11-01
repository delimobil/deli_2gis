package ru.delimobil.deli_dgis

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import by.kirich1409.viewbindingdelegate.viewBinding
import ru.delimobil.deli_dgis.alias.DGisContext
import ru.delimobil.deli_dgis.alias.DGisFile
import ru.delimobil.deli_dgis.data.DGisRenderMode
import ru.delimobil.deli_dgis.data.DGisSource
import ru.delimobil.deli_dgis.databinding.ViewMapDgisBinding
import ru.delimobil.deli_dgis.extensions.openForView
import ru.delimobil.deli_location_provider.DeliLocationProvider
import ru.dgis.sdk.getLocaleManager
import ru.dgis.sdk.map.MapOptions
import ru.dgis.sdk.map.MapRenderMode
import ru.dgis.sdk.map.MapView

class DGisMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val viewBinding by viewBinding(ViewMapDgisBinding::bind)

    init {
        View.inflate(context, R.layout.view_map_dgis, this)
    }

    fun initialize(
        lifecycle: Lifecycle,
        dGisContext: DGisContext,
        deliLocationProvider: DeliLocationProvider,
        style: DGisFile?,
        locales: List<String>? = null,
        sources: List<DGisSource>,
        isClickable: Boolean = true,
        renderMode: DGisRenderMode = DGisRenderMode.Texture,
        onMapReady: (DGisMapHelper) -> Unit
    ) {
        locales?.let { getLocaleManager(dGisContext).overrideLocales(it) }
        val mapOptions = MapOptions().apply {
            style?.let {
                styleFile = it
            }
            this.renderMode = when (renderMode) {
                DGisRenderMode.Texture -> MapRenderMode.TEXTURE
                DGisRenderMode.Surface -> MapRenderMode.SURFACE
            }
        }
        val mapView = MapView(context, mapOptions).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.map_background))
        }
        lifecycle.addObserver(mapView)
        viewBinding.vMapContainer.addView(mapView)
        mapView.setUriOpener { mapView.context.openForView(it) }
        mapView.getMapAsync { map ->
            if (!isClickable) map.interactive = false
            onMapReady.invoke(
                DGisMapHelper(
                    map = map,
                    mapView = mapView,
                    sdkContext = dGisContext,
                    sources = sources,
                    lifecycle = lifecycle,
                    deliLocationProvider = deliLocationProvider
                )
            )
        }
    }
}