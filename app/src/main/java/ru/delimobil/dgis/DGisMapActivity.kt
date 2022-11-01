package ru.delimobil.dgis

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import ru.delimobil.deli_dgis.data.DGisCoordinates
import ru.delimobil.deli_dgis.data.DGisMarkers
import ru.delimobil.deli_dgis.data.DGisSource
import ru.delimobil.deli_dgis.data.options.DGisCameraOptions
import ru.delimobil.deli_dgis.data.options.DGisFillOptions
import ru.delimobil.deli_dgis.data.options.DGisStrokeOptions
import ru.delimobil.deli_dgis.data.polygon.DGisPolygonData
import ru.delimobil.deli_dgis.extensions.getBitmapForDrawable
import ru.delimobil.deli_dgis.provider.Crashlytics
import ru.delimobil.deli_dgis.provider.DeliDGisContextProvider
import ru.delimobil.deli_location_provider.DeliLocationProvider
import ru.delimobil.deli_location_provider.SystemLocationServices
import ru.delimobil.deli_location_provider.helper.LocationHelper
import ru.delimobil.deli_location_provider.helper.SystemLocationHelper
import ru.delimobil.dgis.databinding.ActivityDgisMapBinding

class DGisMapActivity : AppCompatActivity(R.layout.activity_dgis_map) {

    private val viewBinding by viewBinding(ActivityDgisMapBinding::bind)

    private val sources by lazy {
        listOf(
            DGisSource(id = "source_polygons"),
            DGisSource(id = "source_markers")
        )
    }

    private fun getDeliLocationProvider(): DeliLocationProvider {
        return object : DeliLocationProvider(this@DGisMapActivity) {
            override val vendorLocationHelper: LocationHelper
                get() = SystemLocationHelper(SystemLocationServices(this@DGisMapActivity))

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding.vMapOpenAnotherOne.setOnClickListener {
            startActivity(Intent(this, DGisMapActivity::class.java))
        }

        viewBinding.vMap.initialize(
            lifecycle = lifecycle,
            dGisContext = DeliDGisContextProvider(
                this,
                object : Crashlytics {
                    override fun setUserId(userId: String?) {
                        //do nothing
                    }

                    override fun logError(error: Throwable) {
                        //do nothing
                    }

                }
            ).getContext(),
            deliLocationProvider = getDeliLocationProvider(),
            style = null,
            sources = sources,
            onMapReady = { dGisMapHelper ->
                viewBinding.vMapLocationToggle.setOnClickListener {
                    dGisMapHelper.isMyLocationEnabled = !dGisMapHelper.isMyLocationEnabled
                }
                dGisMapHelper.moveCamera(
                    coordinates = listOf(DGisCoordinates(55.758684, 37.619928)),
                    cameraOptions = DGisCameraOptions(
                        animate = false,
                        zoom = 10.5f
                    )
                )

                dGisMapHelper.addPolygons(
                    sourceId = "source_polygons",
                    data = listOf(
                        DGisPolygonData(
                            outlines = listOf(
                                DGisCoordinates(55.760809, 37.582334),
                                DGisCoordinates(55.765475, 37.596472),
                                DGisCoordinates(55.757629, 37.607611),
                                DGisCoordinates(55.750105, 37.602172),
                                DGisCoordinates(55.760809, 37.582334)
                            ),
                            holes = emptyList(),
                            fill = DGisFillOptions(
                                color = ContextCompat.getColor(this, R.color.azure)
                            ),
                            stroke = DGisStrokeOptions(
                                width = 4f,
                                color = ContextCompat.getColor(this, R.color.black)
                            )
                        ),
                        DGisPolygonData(
                            outlines = listOf(
                                DGisCoordinates(55.791736, 37.705464),
                                DGisCoordinates(55.791760, 37.707438),
                                DGisCoordinates(55.789637, 37.706687),
                                DGisCoordinates(55.790355, 37.702889),
                                DGisCoordinates(55.791736, 37.705464)
                            ),
                            holes = emptyList(),
                            fill = DGisFillOptions(
                                color = ContextCompat.getColor(this, R.color.candy_brick)
                            ),
                            stroke = null
                        )
                    )
                )

                val testMarkerPosition = DGisCoordinates(55.771696, 37.701132)
                dGisMapHelper.addMarkers(
                    sourceId = "source_markers",
                    markers = DGisMarkers(
                        groupId = "test",
                        options = listOf(
                            DGisMarkers.Options(
                                id = "test",
                                position = testMarkerPosition,
                                image = DGisMarkers.Options.Icon(
                                    bitmap = getBitmapForDrawable(R.drawable.ic_warning_24_red)
                                )
                            )
                        )
                    )
                )

                dGisMapHelper.setOnCameraMovedListener {
                    viewBinding.vMapMarkerInArea.text = "marker in area: ${
                        dGisMapHelper.doesProjectionContains(testMarkerPosition)
                    }"
                }
            }
        )
    }
}