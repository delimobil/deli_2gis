package ru.delimobil.deli_dgis

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import ru.delimobil.deli_dgis.alias.DGisCameraProjection
import ru.delimobil.deli_dgis.alias.DGisContext
import ru.delimobil.deli_dgis.data.*
import ru.delimobil.deli_dgis.data.options.DGisCameraOptions
import ru.delimobil.deli_dgis.data.options.DGisMapInteractionOptions
import ru.delimobil.deli_dgis.data.polygon.DGisPolygonData
import ru.delimobil.deli_dgis.extensions.hasLocationPermission
import ru.delimobil.deli_dgis.extensions.mapToGeoPointsList
import ru.delimobil.deli_dgis.extensions.toDGisCoordinates
import ru.delimobil.deli_dgis.extensions.toGeoPoint
import ru.delimobil.deli_dgis.map.DGisCompassManager
import ru.delimobil.deli_dgis.map.DGisLocationManager
import ru.delimobil.deli_dgis.turf.TurfTransformation
import ru.delimobil.deli_dgis.turf.TurfUnit
import ru.delimobil.deli_location_provider.DeliLocationProvider
import ru.dgis.sdk.Duration
import ru.dgis.sdk.await
import ru.dgis.sdk.coordinates.Bearing
import ru.dgis.sdk.coordinates.GeoPoint
import ru.dgis.sdk.geometry.GeoPointWithElevation
import ru.dgis.sdk.geometry.Geometry
import ru.dgis.sdk.geometry.PointGeometry
import ru.dgis.sdk.geometry.PolylineGeometry
import ru.dgis.sdk.map.*
import ru.dgis.sdk.map.Map
import ru.dgis.sdk.positioning.registerPlatformLocationSource
import ru.dgis.sdk.positioning.registerPlatformMagneticSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.log2

class DGisMapHelper(
    private val sdkContext: DGisContext,
    private val map: Map,
    private val mapView: MapView,
    private val sources: List<DGisSource>,
    private val lifecycle: Lifecycle,
    private val deliLocationProvider: DeliLocationProvider
) {

    private companion object {
        const val ZOOM_0_SQUARE_SIZE_LPX = 256
        const val CLICK_AREA_SCREEN_DISTANCE = 5f
        const val MAP_CAMERA_SPEED = 300L
        const val SNAPSHOT_JOB_TAG = "map_snapshot"
        const val CAMERA_POSITION_JOB_TAG = "camera_position"
        const val CAMERA_STATE_JOB_TAG = "camera_state"
        const val MAP_DATA_STATE_JOB_TAG = "map_data_state"
    }

    private var innerIdleListener: () -> Unit = {}
    private var innerMovedListener: () -> Unit = {}
    private var innerMoveStartedListener: () -> Unit = {}
    private var innerMarkerClickListener: (DGisMarkerData) -> Unit = {}
    private var innerMapClickListener: (DGisCoordinates) -> Unit = {}
    private var innerMapLongClickListener: (DGisCoordinates) -> Unit = {}
    private var innerMapPaddingChangeListener: (List<Int>) -> Unit = {}

    private val markerJobs = mutableMapOf<String, Job>()
    private val polylineJobs = mutableMapOf<String, Job>()
    private val polygonJob = mutableMapOf<String, Job>()
    private val circleJob = mutableMapOf<String, Job>()
    private val snapshotJob = mutableMapOf<String, Job>()
    private val mapJobs = mutableMapOf<String, AutoCloseable>()

    private val mapPaddings = mutableListOf(0, 0, 0, 0)
    private val imageCacheMap = ConcurrentHashMap<Int, Image>()

    private var isInNavigationMode = false

    private val touchEventsObserver = object : TouchEventsObserver {
        override fun onTap(point: ScreenPoint) {
            map.getRenderedObjects(point, ScreenDistance(CLICK_AREA_SCREEN_DISTANCE))
                .onResult { renderedObjectInfos ->
                    val sourcesIds = sources.map { it.id }
                    renderedObjectInfos
                        .mapNotNull { renderedObjectInfo ->
                            renderedObjectInfo.item.item.userData as? DGisMarkerData
                        }
                        .firstOrNull { dGisMarkerData ->
                            sourcesIds.contains(dGisMarkerData.sourceId)
                        }
                        ?.let { dGisMarkerData ->
                            innerMarkerClickListener.invoke(
                                DGisMarkerData(
                                    markerId = dGisMarkerData.markerId,
                                    sourceId = dGisMarkerData.sourceId
                                )
                            )
                        }
                        ?: cameraProjection?.screenToMap(point)?.let {
                            innerMapClickListener.invoke(it.toDGisCoordinates())
                        }
                }
        }

        override fun onLongTouch(point: ScreenPoint) {
            map.getRenderedObjects(point, ScreenDistance(5f)).onResult { renderedObjectInfos ->
                renderedObjectInfos.getOrNull(0)?.item?.let { _ ->
                    cameraProjection?.screenToMap(point)?.let {
                        innerMapLongClickListener.invoke(
                            it.toDGisCoordinates()
                        )
                    }
                }
            }
        }
    }

    private val mapLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                mapJobs[CAMERA_POSITION_JOB_TAG]?.close()
                mapJobs[CAMERA_POSITION_JOB_TAG] = map.camera
                    .positionChannel.connect {
                        innerMovedListener.invoke()
                    }

                mapJobs[CAMERA_STATE_JOB_TAG]?.close()
                mapJobs[CAMERA_STATE_JOB_TAG] = map.camera
                    .stateChannel.connect { cameraState: CameraState ->
                        if (isInNavigationMode) return@connect
                        when (cameraState) {
                            CameraState.BUSY, CameraState.FLY -> {
                                innerMoveStartedListener.invoke()
                            }
                            CameraState.FREE -> {
                                innerIdleListener.invoke()
                            }
                            else -> {}
                        }
                    }

            }
            Lifecycle.Event.ON_PAUSE -> mapJobs.values.forEach { it.close() }
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> {
                //do nothing
            }
        }
    }

    private val mapSourcesObjectsManagers = mutableMapOf<String, MapObjectManager>()
    private val mapSourcesObjects = mutableMapOf<String, List<SimpleMapObject>>()
    private val mapSourcesZIndex = mutableMapOf<String, Int>()

    private val sourcesDataCache = mutableMapOf<String, Any>()

    private val compassManager by lazy {
        DGisCompassManager(
            context = mapView.context
        )
    }

    private val locationManager by lazy {
        DGisLocationManager(
            context = mapView.context,
            deliLocationProvider = deliLocationProvider
        )
    }

    private val transparentColor by lazy { Color(0, 0, 0, 0) }

    var isMyLocationEnabled: Boolean
        get() = map.sources.find { it is MyLocationMapObjectSource } != null
        @SuppressLint("MissingPermission")
        set(value) {
            when {
                value -> {
                    if (value == isMyLocationEnabled) return
                    onLocationAllowed {
                        registerPlatformMagneticSource(
                            context = sdkContext,
                            source = compassManager
                        )

                        registerPlatformLocationSource(
                            context = sdkContext,
                            source = locationManager
                        )

                        map.addSource(
                            source = MyLocationMapObjectSource(
                                context = sdkContext,
                                directionBehaviour = MyLocationDirectionBehaviour.FOLLOW_MAGNETIC_HEADING,
                                controller = createSmoothMyLocationController()
                            )
                        )
                    }
                }
                else -> {
                    if (value == isMyLocationEnabled) return
                    registerPlatformMagneticSource(
                        context = sdkContext,
                        source = null
                    )

                    registerPlatformLocationSource(
                        context = sdkContext,
                        source = null
                    )

                    map.sources.find { source -> source is MyLocationMapObjectSource }
                        ?.let { locationSource ->
                            locationSource as MyLocationMapObjectSource
                            map.removeSource(locationSource)
                            locationSource.item.close()
                            locationSource.close()
                        }
                }
            }
        }

    var maxZoom: Float
        get() = map.camera.zoomRestrictions.maxZoom.value
        set(value) {
            map.camera.zoomRestrictions = map.camera.zoomRestrictions.copy(maxZoom = Zoom(value))
        }

    val currentZoom: Float
        get() = map.camera.position.zoom.value

    val currentRotation: Double
        get() = map.camera.position.bearing.value

    val currentTarget: DGisCoordinates
        get() = map.camera.position.point.toDGisCoordinates()

    val currentVisibleRegion: DGisRegion?
        get() {
            val projection = cameraProjection ?: return null
            val mapViewRect = Rect()
            mapView.getGlobalVisibleRect(mapViewRect)

            val topLeftPoint = ScreenPoint(
                mapViewRect.left.toFloat() + mapPaddings[0].toFloat(),
                mapViewRect.top.toFloat() + mapPaddings[1].toFloat()
            )

            val topLeft = projection.screenToMap(topLeftPoint) ?: return null

            val topRightPoint = ScreenPoint(
                mapViewRect.right.toFloat() - mapPaddings[2].toFloat(),
                mapViewRect.top.toFloat() + mapPaddings[1].toFloat()
            )

            val topRight = projection.screenToMap(topRightPoint) ?: return null

            val bottomLeftPoint = ScreenPoint(
                mapViewRect.left.toFloat() + mapPaddings[0].toFloat(),
                mapViewRect.bottom.toFloat() - mapPaddings[3].toFloat()
            )

            val bottomLeft = projection.screenToMap(bottomLeftPoint) ?: return null

            val bottomRightPoint = ScreenPoint(
                mapViewRect.right.toFloat() - mapPaddings[2].toFloat(),
                mapViewRect.bottom.toFloat() - mapPaddings[3].toFloat()
            )

            val bottomRight = projection.screenToMap(bottomRightPoint) ?: return null

            val centerXPoint = topLeftPoint.x + ((topRightPoint.x - topLeftPoint.x) / 2)
            val centerYPoint = topLeftPoint.y + ((bottomLeftPoint.y - topLeftPoint.y) / 2)

            val center = projection.screenToMap(
                ScreenPoint(
                    centerXPoint,
                    centerYPoint
                )
            ) ?: return null

            return DGisRegion.fromCoordinates(
                coordinates = listOf(topLeft, topRight, bottomRight, bottomLeft)
                    .map(GeoPoint::toDGisCoordinates),
                center = center.toDGisCoordinates()
            )
        }

    val paddings: List<Int>
        get() = mapPaddings

    val cameraProjection: DGisCameraProjection?
        get() = runCatching {
            map.camera.projection
        }.fold(
            onSuccess = { it },
            onFailure = { null }
        )

    val cameraArea: Geometry?
        get() = runCatching {
            map.camera.visibleArea
        }.fold(
            onSuccess = { it },
            onFailure = { null }
        )

    init {
        map.run {
            val startZIndexes = getStartZIndexes(this@DGisMapHelper.sources)
            this@DGisMapHelper.sources.forEachIndexed { index, dGisSource ->
                mapSourcesObjectsManagers[dGisSource.id] = MapObjectManager(
                    map = map,
                    layerId = dGisSource.anchorLayer
                )
                mapSourcesObjects[dGisSource.id] = listOf()
                mapSourcesZIndex[dGisSource.id] = startZIndexes[index]
            }
            mapView.setTouchEventsObserver(touchEventsObserver)
            lifecycle.addObserver(mapLifecycleObserver)
            if (mapView.measuredHeight != 0) {
                camera.zoomRestrictions = camera.zoomRestrictions.copy(
                    minZoom = Zoom(
                        log2(mapView.measuredHeight.lpx.value / ZOOM_0_SQUARE_SIZE_LPX).absoluteValue
                    )
                )
            }
        }
    }

    fun setOnCameraIdleListener(listener: () -> Unit) {
        innerIdleListener = listener
    }

    fun setOnCameraMovedListener(listener: () -> Unit) {
        innerMovedListener = listener
    }

    fun setOnCameraMoveStartedListener(listener: () -> Unit) {
        innerMoveStartedListener = listener
    }

    fun setOnMarkerClickListener(listener: (DGisMarkerData) -> Unit) {
        innerMarkerClickListener = listener
    }

    fun setOnMapClickListener(listener: (DGisCoordinates) -> Unit) {
        innerMapClickListener = listener
    }

    fun setOnMapLongClickListener(listener: (DGisCoordinates) -> Unit) {
        innerMapLongClickListener = listener
    }

    fun setOnMapPaddingChangeListener(listener: (List<Int>) -> Unit) {
        innerMapPaddingChangeListener = listener
    }

    fun setMapInteractions(interactionOptions: DGisMapInteractionOptions) {
        mapView.gestureManager?.run {
            switchGesture(Gesture.ROTATION, interactionOptions.rotationEnabled)
            switchGesture(Gesture.TILT, interactionOptions.tiltEnabled)
            switchGesture(Gesture.SCALING, interactionOptions.scalingEnabled)
            switchGesture(Gesture.MULTI_TOUCH_SHIFT, false)
        }
    }

    private fun GestureManager.switchGesture(gesture: Gesture, isEnabled: Boolean) {
        val currentEnable = gestureEnabled(gesture)
        when {
            isEnabled && !currentEnable -> enableGesture(gesture)
            !isEnabled && currentEnable -> disableGesture(gesture)
            else -> {
                // do nothing
            }
        }
    }

    fun addPolyline(sourceId: String, polyline: DGisPolyline) {
        if (sourcesDataCache[sourceId] == polyline) return
        polylineJobs[polyline.groupId]?.cancel()
        polylineJobs[polyline.groupId] = CoroutineScope(Dispatchers.IO).launch {
            if (polyline.options.points.size >= 2) {
                val polylineObject = Polyline(
                    PolylineOptions(
                        points = polyline.options.points.map { point ->
                            GeoPoint(point.lat, point.lon)
                        },
                        width = polyline.options.width.lpx,
                        color = Color(polyline.options.color)
                    )
                )

                withContext(Dispatchers.Main) {
                    addObjectsToSource(sourceId, listOf(polylineObject))
                    sourcesDataCache[sourceId] = polyline
                }
            }
        }
    }

    fun addMarkers(
        sourceId: String,
        markers: DGisMarkers
    ) {
        if (sourcesDataCache[sourceId] == markers) return
        markerJobs[markers.groupId]?.cancel()
        markerJobs[markers.groupId] = CoroutineScope(Dispatchers.IO).launch {
            val icons = mutableSetOf<DGisMarkers.Options.Icon>()
            val markersFeatures = markers.options.filter {
                it.visible
            }.mapIndexed { index, markerOptions ->
                val pos = markerOptions.position
                markerOptions.image?.let(icons::add)

                Marker(
                    MarkerOptions(
                        position = GeoPointWithElevation(
                            latitude = pos.lat,
                            longitude = pos.lon
                        ),
                        iconOpacity = Opacity(markerOptions.alpha ?: 1f),
                        iconMapDirection = markerOptions.rotation?.let { MapDirection(it.toDouble()) },
                        icon = markerOptions.image?.bitmap?.let { bitmap ->
                            imageCacheMap[bitmap.generationId]
                                ?: imageFromBitmap(
                                    sdkContext,
                                    bitmap
                                ).also { image ->
                                    imageCacheMap[bitmap.generationId] = image
                                }
                        },
                        userData = DGisMarkerData(
                            markerId = markerOptions.id,
                            sourceId = sourceId
                        ),
                        anchor = Anchor().copy(
                            y = markerOptions.verticalAnchor.value,
                            x = markerOptions.horizontalAnchor.value
                        ),
                        zIndex = ZIndex(
                            getSourceZIndex(sourceId).value
                                .plus(markerOptions.zIndex ?: index)
                        )
                    )
                )
            }
            withContext(Dispatchers.Main) {
                addObjectsToSource(sourceId, markersFeatures)
                sourcesDataCache[sourceId] = markers
            }
        }
    }

    fun addPolygons(
        sourceId: String,
        data: List<DGisPolygonData>
    ) {
        if (sourcesDataCache[sourceId] == data) return
        polygonJob[sourceId]?.cancel()
        polygonJob[sourceId] = CoroutineScope(Dispatchers.IO).launch {
            val polygonsAndPatternPolylines = mutableListOf<SimpleMapObject>().apply {
                data.forEach { dGisPolygonData ->
                    dGisPolygonData.outlines.mapToGeoPointsList()?.let { polygonOutlines ->
                        val polygonHoles =
                            dGisPolygonData.holes.mapNotNull { it.mapToGeoPointsList() }
                        var strokeColor = transparentColor
                        var strokeWidth = 0f

                        dGisPolygonData.stroke?.let { stroke ->
                            stroke.pattern?.let { strokePattern ->
                                add(
                                    Polyline(
                                        options = PolylineOptions(
                                            points = polygonOutlines,
                                            color = Color(stroke.color),
                                            width = stroke.width.lpx,
                                            dashedPolylineOptions = DashedPolylineOptions(
                                                dashLength = strokePattern.dash.lpx,
                                                dashSpaceLength = strokePattern.gap.lpx
                                            ),
                                            zIndex = getSourceZIndex(sourceId)
                                        )
                                    )
                                )
                            } ?: kotlin.run {
                                strokeColor = Color(stroke.color)
                                strokeWidth = stroke.width
                            }
                        }
                        add(
                            Polygon(
                                options = PolygonOptions(
                                    contours = mutableListOf(polygonOutlines).apply {
                                        addAll(polygonHoles)
                                    },
                                    color = dGisPolygonData.fill?.color?.let {
                                        Color(it)
                                    } ?: transparentColor,
                                    strokeColor = strokeColor,
                                    strokeWidth = strokeWidth.lpx,
                                    zIndex = getSourceZIndex(sourceId)
                                )
                            )
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                addObjectsToSource(sourceId, polygonsAndPatternPolylines)
                sourcesDataCache[sourceId] = data
            }
        }
    }

    fun addCircles(
        sourceId: String,
        dGisCircles: DGisCircles
    ) {
        if (sourcesDataCache[sourceId] == dGisCircles) return
        circleJob[dGisCircles.groupId]?.cancel()
        circleJob[dGisCircles.groupId] = CoroutineScope(Dispatchers.IO).launch {
            val circlesPolygonsAndPatternPolylines = mutableListOf<SimpleMapObject>().apply {
                dGisCircles.options.map { option ->
                    val centerPoint = GeoPoint(option.center.lat, option.center.lon)
                    option to TurfTransformation.circle(
                        center = centerPoint,
                        radius = option.radius.toDouble(),
                        unit = TurfUnit.METERS
                    )
                }
                    .filter { (_, circleOutlines) ->
                        circleOutlines.distinct().size > 1
                    }
                    .forEach { (dGisCirclesOptions, circleOutlines) ->
                        var strokeColor = transparentColor
                        var strokeWidth = 0f

                        dGisCirclesOptions.stroke?.let { stroke ->
                            stroke.pattern?.let { strokePattern ->
                                add(
                                    Polyline(
                                        options = PolylineOptions(
                                            points = circleOutlines,
                                            color = Color(stroke.color),
                                            width = stroke.width.lpx,
                                            dashedPolylineOptions = DashedPolylineOptions(
                                                dashLength = strokePattern.dash.lpx,
                                                dashSpaceLength = strokePattern.gap.lpx
                                            ),
                                            zIndex = getSourceZIndex(sourceId)
                                        )
                                    )
                                )
                            } ?: kotlin.run {
                                strokeColor = Color(stroke.color)
                                strokeWidth = stroke.width
                            }
                        }
                        add(
                            Polygon(
                                options = PolygonOptions(
                                    contours = mutableListOf(circleOutlines),
                                    color = Color(dGisCirclesOptions.fill.color),
                                    strokeColor = strokeColor,
                                    strokeWidth = strokeWidth.lpx,
                                    zIndex = getSourceZIndex(sourceId)
                                )
                            )
                        )
                    }
            }

            withContext(Dispatchers.Main) {
                addObjectsToSource(sourceId, circlesPolygonsAndPatternPolylines)
                sourcesDataCache[sourceId] = dGisCircles
            }
        }
    }

    fun clearSource(sourceId: String) {
        mapSourcesObjectsManagers[sourceId]?.removeAll()
        mapSourcesObjects[sourceId] = emptyList()
        sourcesDataCache.remove(sourceId)
    }

    fun setSourceVisible(sourceId: String, visible: Boolean) {
        mapSourcesObjectsManagers[sourceId]?.isVisible = visible
    }

    private fun addObjectsToSource(sourceId: String, mapObjects: List<SimpleMapObject>) {
        val oldSourceObjects = mapSourcesObjects[sourceId] ?: emptyList()
        mapSourcesObjects[sourceId] = mapObjects
        mapSourcesObjectsManagers[sourceId]?.removeAndAddObjects(oldSourceObjects, mapObjects)
    }

    fun doesProjectionContains(coordinates: DGisCoordinates): Boolean {
        return cameraArea?.intersects(PointGeometry(coordinates.toGeoPoint())) ?: false
    }

    fun switchToNavigationMode(coordinates: DGisCoordinates, zoom: Float, tilt: Float) {
        if (isInNavigationMode) return
        moveCamera(
            coordinates = listOf(coordinates),
            cameraOptions = DGisCameraOptions(
                zoom = zoom,
                tilt = tilt,
                animate = true
            ),
            onMoveCompleted = {
                isInNavigationMode = true
                mapView.gestureManager?.run {
                    switchGesture(Gesture.SHIFT, false)
                }
                map.camera.setBehaviour(
                    CameraBehaviour(
                        FollowPosition(
                            bearing = FollowBearing.MAGNETIC,
                            tilt = FollowTilt.ON,
                            zoom = FollowZoom.ON
                        )
                    )
                )
            }
        )
    }

    fun switchFromNavigationMode() {
        if (!isInNavigationMode) return
        isInNavigationMode = false
        mapView.gestureManager?.run {
            switchGesture(Gesture.SHIFT, true)
        }
    }

    fun moveCamera(
        coordinates: List<DGisCoordinates>,
        cameraOptions: DGisCameraOptions,
        onMoveCompleted: (() -> Unit)? = null
    ) {
        if (coordinates.isEmpty() || isInNavigationMode) return

        val zoom = cameraOptions.zoom?.let { Zoom(it) } ?: map.camera.position.zoom
        val tilt = cameraOptions.tilt?.let { Tilt(it) } ?: map.camera.position.tilt
        val bearing = cameraOptions.bearing?.let { Bearing(it) } ?: map.camera.position.bearing
        map.camera.setPadding(
            Padding(
                mapPaddings[0] + cameraOptions.padding,
                mapPaddings[1] + cameraOptions.padding,
                mapPaddings[2] + cameraOptions.padding,
                mapPaddings[3] + cameraOptions.padding
            )
        )
        val cameraPosition = when (coordinates.size) {
            1 -> {
                CameraPosition(
                    point = coordinates.first().toGeoPoint(),
                    zoom = zoom,
                    tilt = tilt,
                    bearing = bearing
                )
            }
            else -> {
                val dGisLatLngBounds = DGisLatLngBounds.fromCoordinates(coordinates)
                calcPosition(
                    camera = map.camera,
                    geometry = PolylineGeometry(
                        points = listOf(
                            dGisLatLngBounds.getNorthEast().toGeoPoint(),
                            dGisLatLngBounds.getSouthWest().toGeoPoint()
                        )
                    ),
                    tilt = tilt,
                    bearing = bearing
                )
            }
        }

        when {
            cameraPosition.point.isValid -> map.camera.move(
                cameraPosition,
                Duration.ofMilliseconds(if (cameraOptions.animate) MAP_CAMERA_SPEED else 0),
                CameraAnimationType.DEFAULT
            ).onResult { onMoveCompleted?.invoke() }

            else -> onMoveCompleted?.invoke()
        }
    }

    private fun getSourceZIndex(sourceId: String): ZIndex {
        return mapSourcesZIndex[sourceId]?.let { zIndex -> ZIndex(zIndex) } ?: ZIndex(0)
    }

    fun zoomOut() {
        changeZoom(-1f)
    }

    fun zoomIn() {
        changeZoom(1f)
    }

    fun getMapSnapshot(result: (Bitmap?) -> Unit) {
        when (map.dataLoadingState) {
            MapDataLoadingState.LOADING -> {
                mapJobs[MAP_DATA_STATE_JOB_TAG]?.close()
                mapJobs[MAP_DATA_STATE_JOB_TAG] = map.dataLoadingStateChannel.connect { state ->
                    when (state) {
                        MapDataLoadingState.LOADED -> getMapSnapshot(result)
                        else -> {
                            // wait
                        }
                    }
                }
            }
            MapDataLoadingState.LOADED -> {
                snapshotJob[SNAPSHOT_JOB_TAG]?.cancel()
                snapshotJob[SNAPSHOT_JOB_TAG] = CoroutineScope(Dispatchers.Main).launch {
                    result.invoke(
                        try {
                            mapView.takeSnapshot(copyrightPosition = Alignment.BOTTOM_RIGHT)
                                .await()
                                .toBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    )
                }
            }
        }
    }

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        mapPaddings[0] = left
        mapPaddings[1] = top
        mapPaddings[2] = right
        mapPaddings[3] = bottom
        innerMapPaddingChangeListener.invoke(mapPaddings)
    }

    fun setLogoPosition(position: DGisLogoPosition) {
        mapView.run {
            setCopyrightGravity(
                gravity = position.gravity
            )
            setCopyrightMargins(
                left = position.paddingLeft,
                top = position.paddingTop,
                right = position.paddingRight,
                bottom = position.paddingBottom
            )
        }
    }

    private fun destroy() {
        imageCacheMap.clear()
        sourcesDataCache.clear()

        listOf(
            snapshotJob,
            markerJobs,
            polylineJobs,
            polygonJob,
            circleJob
        ).forEach { jobsMap ->
            jobsMap.forEach { it.value.cancel() }
            jobsMap.clear()
        }

        mapJobs.run {
            values.forEach { it.close() }
            clear()
        }
        mapSourcesObjectsManagers.run {
            values.forEach { it.close() }
            clear()
        }

        innerIdleListener = {}
        innerMapClickListener = {}
        innerMovedListener = {}
        innerMapLongClickListener = {}
        innerMarkerClickListener = {}
        innerMoveStartedListener = {}
        innerMapPaddingChangeListener = {}
    }


    private fun changeZoom(delta: Float) {
        map.camera.move(
            map.camera.position.copy(
                zoom = Zoom(currentZoom + delta)
            ),
            Duration.ofMilliseconds(500),
            CameraAnimationType.DEFAULT
        )
    }

    private fun removeSource(sourceId: String) {
        mapSourcesZIndex.remove(sourceId)
        mapSourcesObjects.remove(sourceId)
        mapSourcesObjectsManagers.remove(sourceId)
    }

    private fun onLocationAllowed(allow: () -> Unit) {
        if (mapView.context.hasLocationPermission()) {
            allow.invoke()
        }
    }

    private fun getStartZIndexes(sources: List<DGisSource>): List<Int> {
        val sourcesCount = sources.size
        val result = mutableListOf<Int>()
        val sourceStep = Int.MAX_VALUE / sourcesCount
        for (i in 0 until sourcesCount) {
            result.add(i * sourceStep)
        }
        return result
    }
}