package org.odk.collect.mapbox

import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.startup.AppInitializer
import com.google.android.gms.location.LocationListener
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.layers.Layer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.RasterLayer
import com.mapbox.maps.extension.style.sources.Source
import com.mapbox.maps.extension.style.sources.TileSet
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.RasterSource
import com.mapbox.maps.extension.style.sources.generated.VectorSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.loader.MapboxMapsInitializer
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolygonAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.addOnScaleListener
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.odk.collect.androidshared.utils.ScreenUtils
import org.odk.collect.location.LocationClient
import org.odk.collect.location.LocationClient.LocationClientListener
import org.odk.collect.maps.LineDescription
import org.odk.collect.maps.MapFragment
import org.odk.collect.maps.MapFragment.ErrorListener
import org.odk.collect.maps.MapFragment.FeatureListener
import org.odk.collect.maps.MapFragment.PointListener
import org.odk.collect.maps.MapFragment.ReadyListener
import org.odk.collect.maps.MapFragmentDelegate
import org.odk.collect.maps.MapPoint
import org.odk.collect.maps.PolygonDescription
import org.odk.collect.maps.layers.MapFragmentReferenceLayerUtils.getReferenceLayerFile
import org.odk.collect.maps.layers.MbtilesFile
import org.odk.collect.maps.layers.ReferenceLayerRepository
import org.odk.collect.maps.markers.MarkerDescription
import org.odk.collect.maps.markers.MarkerIconCreator
import org.odk.collect.maps.markers.MarkerIconDescription
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.shared.injection.ObjectProviderHost
import timber.log.Timber
import java.io.File
import java.io.IOException

class MapboxMapFragment :
    Fragment(),
    MapFragment,
    OnMapClickListener,
    OnMapLongClickListener,
    LocationListener,
    LocationClientListener {

    private lateinit var mapView: MapView
    private lateinit var mapboxMap: MapboxMap

    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager

    private var mapReadyListener: ReadyListener? = null
    private val gpsLocationReadyListeners = mutableListOf<ReadyListener>()

    private var nextFeatureId = 1
    private val features = mutableMapOf<Int, MapFeature>()

    private var gpsLocationListener: PointListener? = null
    private var clickListener: PointListener? = null
    private var longPressListener: PointListener? = null

    private var featureClickListener: FeatureListener? = null
    private var featureDragEndListener: FeatureListener? = null

    private var lastLocationProvider: String? = null
    private var lastLocationFix: MapPoint? = null
    private var tileServer: TileHttpServer? = null
    private var referenceLayerFile: File? = null
    private var clientWantsLocationUpdates = false
    private var topStyleLayerId: String? = null
    private val locationCallback = MapboxLocationCallback(this)
    override val mapFragmentDelegate = MapFragmentDelegate(
        this,
        { MapboxMapConfigurator() },
        { settingsProvider.getUnprotectedSettings() },
        { settingsProvider.getMetaSettings() },
        this::onConfigChanged
    )

    private var hasCenter = false

    private val settingsProvider: SettingsProvider by lazy {
        (requireActivity().applicationContext as ObjectProviderHost).getObjectProvider().provide(SettingsProvider::class.java)
    }

    private val referenceLayerRepository: ReferenceLayerRepository by lazy {
        (requireActivity().applicationContext as ObjectProviderHost).getObjectProvider().provide(ReferenceLayerRepository::class.java)
    }

    private val locationClient: LocationClient by lazy {
        (requireActivity().applicationContext as ObjectProviderHost).getObjectProvider().provide(LocationClient::class.java)
    }

    override fun init(readyListener: ReadyListener?, errorListener: ErrorListener?) {
        mapReadyListener = readyListener

        // Mapbox SDK only knows how to fetch tiles via HTTP. If we want it to
        // display tiles from a local file, we have to serve them locally over HTTP.
        try {
            tileServer = TileHttpServer().also {
                it.start()
            }
        } catch (e: IOException) {
            Timber.e(e, "Could not start the TileHttpServer")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppInitializer.getInstance(requireContext()).initializeComponent(MapboxMapsInitializer::class.java)
        mapFragmentDelegate.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mapView = MapView(inflater.context).apply {
            compass.position = Gravity.TOP or Gravity.START
            compass.marginTop = 36f
            compass.marginBottom = 36f
            compass.marginLeft = 36f
            compass.marginRight = 36f
        }

        mapboxMap = mapView
            .getMapboxMap()
            .apply {
                addOnMapClickListener(this@MapboxMapFragment)
                addOnMapLongClickListener(this@MapboxMapFragment)
                addOnScaleListener(object : OnScaleListener {
                    override fun onScale(detector: StandardScaleGestureDetector) = Unit

                    override fun onScaleBegin(detector: StandardScaleGestureDetector) = Unit

                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                        mapFragmentDelegate.onZoomLevelChangedByUserListener(cameraState.zoom.toFloat())
                    }
                })
            }

        polylineAnnotationManager = mapView
            .annotations
            .createPolylineAnnotationManager()

        pointAnnotationManager = mapView
            .annotations
            .createPointAnnotationManager()

        initLocationComponent()
        moveOrAnimateCamera(MapFragment.INITIAL_CENTER, false, MapFragment.INITIAL_ZOOM.toDouble())

        // If the screen is rotated before the map is ready, this fragment could already be detached,
        // which makes it unsafe to use. Only call the ReadyListener if this fragment is still attached.
        if (mapReadyListener != null && activity != null) {
            mapFragmentDelegate.onReady()
            mapReadyListener!!.onReady(this)
        }

        return mapView
    }

    override fun onStart() {
        super.onStart()
        mapFragmentDelegate.onStart()
    }

    override fun onResume() {
        super.onResume()
        enableLocationUpdates(clientWantsLocationUpdates)
    }

    override fun onPause() {
        super.onPause()
        enableLocationUpdates(false)
    }

    override fun onStop() {
        mapFragmentDelegate.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapFragmentDelegate.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        tileServer?.destroy()
        MarkerIconCreator.clearCache()
        super.onDestroy()
    }

    private fun onConfigChanged(config: Bundle) {
        val styleUrl = config.getString(KEY_STYLE_URL) ?: Style.MAPBOX_STREETS
        referenceLayerFile = getReferenceLayerFile(config, referenceLayerRepository)
        mapboxMap.loadStyleUri(styleUrl) {
            if (topStyleLayerId == null) {
                // remember the id of the top style layer
                topStyleLayerId = it.styleLayers.last().id
            }
            loadReferenceOverlay()
        }
    }

    override fun getCenter(): MapPoint {
        val point = mapboxMap.cameraState.center
        return MapPoint(point.latitude(), point.longitude())
    }

    override fun getZoom(): Double {
        return mapboxMap.cameraState.zoom
    }

    override fun setCenter(center: MapPoint?, animate: Boolean) {
        center?.let {
            moveOrAnimateCamera(it, animate)
        }

        hasCenter = true
    }

    override fun zoomToPoint(center: MapPoint?, animate: Boolean) {
        zoomToPoint(center, MapFragment.POINT_ZOOM.toDouble(), animate)
    }

    override fun zoomToPoint(center: MapPoint?, zoom: Double, animate: Boolean) {
        center?.let {
            moveOrAnimateCamera(it, animate, zoom)
        }

        hasCenter = true
    }

    override fun zoomToBoundingBox(
        mapPoints: Iterable<MapPoint>?,
        scaleFactor: Double,
        animate: Boolean
    ) {
        mapPoints?.let {
            val points = mapPoints.map {
                Point.fromLngLat(it.longitude, it.latitude, it.altitude)
            }

            val screenWidth = ScreenUtils.getScreenWidth(context)
            val screenHeight = ScreenUtils.getScreenHeight(context)

            lifecycleScope.launch {
                delay(100L)
                mapboxMap.setCamera(
                    mapboxMap.cameraForCoordinates(
                        points,
                        EdgeInsets(
                            screenHeight / 5.0,
                            screenWidth / 5.0,
                            screenHeight / 5.0,
                            screenWidth / 5.0
                        )
                    )
                )
            }
        }

        hasCenter = true
    }

    override fun addMarker(markerDescription: MarkerDescription): Int {
        return addMarkers(listOf(markerDescription)).first()
    }

    override fun addMarkers(markers: List<MarkerDescription>): List<Int> {
        val pointAnnotations =
            MapUtils.createPointAnnotations(requireContext(), pointAnnotationManager, markers)

        val featureIds = mutableListOf<Int>()
        markers.asSequence()
            .zip(pointAnnotations.asSequence())
            .forEach { (marker, pointAnnotation) ->
                val featureId = nextFeatureId++
                val markerFeature = MarkerFeature(
                    requireContext(),
                    pointAnnotationManager,
                    pointAnnotation,
                    featureId,
                    featureClickListener,
                    featureDragEndListener,
                    marker.point
                )

                featureIds.add(featureId)
                features[featureId] = markerFeature
            }

        return featureIds
    }

    override fun setMarkerIcon(featureId: Int, markerIconDescription: MarkerIconDescription) {
        val feature = features[featureId]
        if (feature is MarkerFeature) {
            feature.setIcon(markerIconDescription)
        }
    }

    override fun getMarkerPoint(featureId: Int): MapPoint? {
        val feature = features[featureId]
        return if (feature is MarkerFeature) {
            feature.point
        } else {
            null
        }
    }

    override fun addPolyLine(lineDescription: LineDescription): Int {
        val featureId = nextFeatureId++
        if (lineDescription.draggable) {
            features[featureId] = DynamicPolyLineFeature(
                requireContext(),
                pointAnnotationManager,
                polylineAnnotationManager,
                featureId,
                featureClickListener,
                featureDragEndListener,
                lineDescription
            )
        } else {
            features[featureId] = StaticPolyLineFeature(
                polylineAnnotationManager,
                featureId,
                featureClickListener,
                lineDescription
            )
        }
        return featureId
    }

    override fun addPolygon(polygonDescription: PolygonDescription): Int {
        val featureId = nextFeatureId++
        features[featureId] = StaticPolygonFeature(
            mapView.annotations.createPolygonAnnotationManager(),
            polygonDescription,
            featureClickListener,
            featureId
        )

        return featureId
    }

    override fun appendPointToPolyLine(featureId: Int, point: MapPoint) {
        val feature = features[featureId]
        if (feature is DynamicPolyLineFeature) {
            feature.appendPoint(point)
        }
    }

    override fun removePolyLineLastPoint(featureId: Int) {
        val feature = features[featureId]
        if (feature is DynamicPolyLineFeature) {
            feature.removeLastPoint()
        }
    }

    override fun getPolyLinePoints(featureId: Int): List<MapPoint> {
        val feature = features[featureId]
        return if (feature is LineFeature) {
            feature.points
        } else {
            emptyList()
        }
    }

    override fun clearFeatures() {
        for (feature in features.values) {
            feature.dispose()
        }

        features.clear()
        nextFeatureId = 1
    }

    override fun setClickListener(listener: PointListener?) {
        clickListener = listener
    }

    override fun setLongPressListener(listener: PointListener?) {
        longPressListener = listener
    }

    override fun setFeatureClickListener(listener: FeatureListener?) {
        featureClickListener = listener
    }

    override fun setDragEndListener(listener: FeatureListener?) {
        featureDragEndListener = listener
    }

    override fun setGpsLocationEnabled(enabled: Boolean) {
        if (enabled != clientWantsLocationUpdates) {
            clientWantsLocationUpdates = enabled
            enableLocationUpdates(clientWantsLocationUpdates)
        }
    }

    override fun getGpsLocation(): MapPoint? {
        return lastLocationFix
    }

    override fun getLocationProvider(): String? {
        return lastLocationProvider
    }

    override fun runOnGpsLocationReady(listener: ReadyListener) {
        if (lastLocationFix != null) {
            listener.onReady(this)
        } else {
            gpsLocationReadyListeners.add(listener)
        }
    }

    override fun setGpsLocationListener(listener: PointListener?) {
        gpsLocationListener = listener
    }

    override fun setRetainMockAccuracy(retainMockAccuracy: Boolean) {
        locationCallback.setRetainMockAccuracy(retainMockAccuracy)
    }

    override fun hasCenter(): Boolean {
        return hasCenter
    }

    override fun onMapClick(point: Point): Boolean {
        clickListener?.onPoint(MapPoint(point.latitude(), point.longitude()))

        // MAPBOX ISSUE: Unfortunately, onMapClick is called before onAnnotationClick,
        // which means that every click on a marker will also cause a click event on
        // the map. Returning true will consume the event and prevent the marker's
        // onAnnotationClick from ever being called, so we have to return false.
        return false
    }

    override fun onMapLongClick(point: Point): Boolean {
        longPressListener?.onPoint(MapPoint(point.latitude(), point.longitude()))
        return true
    }

    override fun onLocationChanged(location: Location) {
        lastLocationFix = MapPoint(
            location.latitude,
            location.longitude,
            location.altitude,
            location.accuracy.toDouble()
        )
        lastLocationProvider = location.provider
        Timber.i(
            "Received location update: %s (%s)",
            lastLocationFix,
            lastLocationProvider
        )
        for (listener in gpsLocationReadyListeners) {
            listener.onReady(this)
        }
        gpsLocationReadyListeners.clear()
        gpsLocationListener?.onPoint(lastLocationFix!!)
    }

    @SuppressWarnings("MissingPermission") // permission checks for location services are handled in widgets
    private fun enableLocationUpdates(enabled: Boolean) {
        if (enabled) {
            Timber.i("Starting LocationClient %s (for MapFragment %s)", locationClient, this)
            locationClient.start(this)
        } else {
            Timber.i("Stopping LocationClient %s (for MapFragment %s)", locationClient, this)
            locationClient.stop()
        }

        mapView.location.enabled = enabled
    }

    private fun initLocationComponent() {
        mapView.location.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                AppCompatResources.getDrawable(
                    requireContext(),
                    org.odk.collect.maps.R.drawable.ic_crosshairs
                )
            )
        }
    }

    private fun moveOrAnimateCamera(point: MapPoint, animate: Boolean, zoom: Double = getZoom()) {
        mapboxMap.flyTo(
            cameraOptions {
                center(Point.fromLngLat(point.longitude, point.latitude, point.altitude))
                zoom(zoom)
            },
            mapAnimationOptions {
                duration(if (animate) 300 else 0)
            }
        )
    }

    private fun loadReferenceOverlay() {
        referenceLayerFile?.let {
            addMbtiles(it.name, referenceLayerFile!!)
        }
    }

    private fun addMbtiles(id: String, file: File) {
        tileServer?.let {
            val mbtiles: MbtilesFile = try {
                MbtilesFile(file)
            } catch (e: MbtilesFile.MbtilesException) {
                Timber.w(e.message)
                return
            }

            val tileSet = createTileSet(mbtiles, it.getUrlTemplate(id))
            it.addSource(id, mbtiles)

            if (mbtiles.layerType == MbtilesFile.LayerType.VECTOR) {
                addOverlaySource(VectorSource.Builder(id).tileSet(tileSet).build())
                for (layer in mbtiles.vectorLayers) {
                    // Pick a colour that's a function of the filename and layer name.
                    // The colour will appear essentially random; the only purpose here
                    // is to try to assign different colours to different layers, such
                    // that each individual layer appears in its own consistent colour.
                    val hue = ((id + "." + layer.name).hashCode() and 0x7fffffff) % 360
                    addOverlayLayer(
                        LineLayer(id + "." + layer.name, id)
                            .lineColor(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.7f, 1f)))
                            .lineWidth(1.0)
                            .lineOpacity(0.7)
                            .sourceLayer(layer.name)
                    )
                }
            }
            if (mbtiles.layerType == MbtilesFile.LayerType.RASTER) {
                addOverlaySource(RasterSource.Builder(id).tileSet(tileSet).build())
                addOverlayLayer(RasterLayer(id + ".raster", id))
            }
            Timber.i("Added %s as a %s layer at /%s", file, mbtiles.layerType, id)
        }
    }

    private fun createTileSet(mbtiles: MbtilesFile, urlTemplate: String): TileSet {
        val tileSet = TileSet.Builder("2.2.0", listOf(urlTemplate))

        // Configure the TileSet using the metadata in the .mbtiles file.
        try {
            tileSet.name(mbtiles.getMetadata("name"))
            try {
                tileSet.minZoom(mbtiles.getMetadata("minzoom").toInt())
                tileSet.maxZoom(mbtiles.getMetadata("maxzoom").toInt())
            } catch (e: NumberFormatException) {
                // ignore
            }
            var parts = mbtiles.getMetadata("center").split(",").toTypedArray()
            if (parts.size == 3) { // latitude, longitude, zoom
                try {
                    tileSet.center(
                        listOf(
                            parts[0].toDouble(),
                            parts[1].toDouble(),
                            parts[2].toDouble()
                        )
                    )
                } catch (e: NumberFormatException) {
                    // ignore
                }
            }
            parts = mbtiles.getMetadata("bounds").split(",").toTypedArray()
            if (parts.size == 4) { // left, bottom, right, top
                try {
                    tileSet.bounds(
                        listOf(
                            parts[0].toDouble(),
                            parts[1].toDouble(),
                            parts[2].toDouble(),
                            parts[3].toDouble()
                        )
                    )
                } catch (e: NumberFormatException) {
                    // ignore
                }
            }
        } catch (e: MbtilesFile.MbtilesException) {
            Timber.w(e.message)
        }
        return tileSet.build()
    }

    private fun addOverlayLayer(layer: Layer) {
        topStyleLayerId?.let {
            mapboxMap.getStyle()?.addLayerAbove(layer, topStyleLayerId)
        }
    }

    private fun addOverlaySource(source: Source) {
        if (mapboxMap.getStyle()?.getSource(source.sourceId) == null) {
            mapboxMap.getStyle()?.addSource(source)
        }
    }

    override fun onClientStart() {
        Timber.i("Requesting location updates (to %s)", this)
        locationClient.requestLocationUpdates(this)
    }

    override fun onClientStartFailure() {
    }

    override fun onClientStop() {
    }

    companion object {
        const val KEY_STYLE_URL = "STYLE_URL"
    }
}
