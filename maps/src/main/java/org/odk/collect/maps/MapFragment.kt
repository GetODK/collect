package org.odk.collect.maps

import androidx.annotation.StringDef
import org.odk.collect.maps.markers.MarkerDescription
import org.odk.collect.maps.markers.MarkerIconDescription

/**
 * Interface for a Fragment that renders a map view.  The plan is to have one
 * implementation for each map SDK, e.g. GoogleMapFragment, OsmDroidMapFragment, etc.
 *
 * This is intended to be a single map API that provides all functionality needed
 * for the three geo widgets (collecting or editing a point, a trace, or a shape):
 *   - Basic control of the viewport (panning, zooming)
 *   - Displaying and getting the current GPS location
 *   - Requesting a callback on the first GPS location fix
 *   - Requesting callbacks for short clicks and long presses on the map
 *   - Adding editable points to the map
 *   - Adding editable traces (polylines) to the map
 *   - Adding editable shapes (closed polygons) to the map
 *
 * Editable points, traces, and shapes are called "map features" in this API.
 * To keep the API small, features are not exposed as objects; instead, they are
 * identified by integer feature IDs.  To keep the API unified (instead of having
 * three distinct modes), the map always supports all three kinds of features,
 * even though the geo widgets only use one kind of feature at a time.
 */
interface MapFragment {
    val mapFragmentDelegate: MapFragmentDelegate

    fun init(readyListener: ReadyListener?, errorListener: ErrorListener?)

    /** Gets the point currently shown at the center of the map view.  */
    fun getCenter(): MapPoint

    /**
     * Gets the current zoom level.  For maps that only support zooming by
     * powers of 2, the zoom level will always be an integer.
     */
    fun getZoom(): Double

    /**
     * Centers the map view on the given point, leaving zoom level unchanged,
     * possibly with animation.
     */
    fun setCenter(center: MapPoint?, animate: Boolean)

    /**
     * Centers the map view on the current location, zooming in to the last zoom level set by the
     * user if available, or to a close-up level deemed appropriate by
     * the implementation, possibly with animation.
     */
    fun zoomToCurrentLocation(center: MapPoint?) {
        zoomToPoint(
            center,
            mapFragmentDelegate.zoomLevel?.toDouble() ?: POINT_ZOOM.toDouble(),
            true
        )
    }

    /**
     * Centers the map view on the given point, zooming in to a close-up level
     * deemed appropriate by the implementation, possibly with animation.
     */
    fun zoomToPoint(center: MapPoint?, animate: Boolean)

    /**
     * Centers the map view on the given point with a zoom level as close as
     * possible to the given zoom level, possibly with animation.
     */
    fun zoomToPoint(center: MapPoint?, zoom: Double, animate: Boolean)

    /**
     * Adjusts the map's viewport to enclose all of the given points, possibly
     * with animation.  A scaleFactor of 1.0 ensures that all the points will be
     * just visible in the viewport; a scaleFactor less than 1 shrinks the view
     * beyond that.  For example, a scaleFactor of 0.8 causes the bounding box
     * to occupy at most 80% of the width and 80% of the height of the viewport,
     * ensuring a margin of at least 10% on all sides.
     */
    fun zoomToBoundingBox(points: Iterable<MapPoint>?, scaleFactor: Double, animate: Boolean)

    /**
     * Adds a marker to the map at the given location. If draggable is true,
     * the user will be able to drag the marker to change its location.
     * Returns a positive integer, the featureId for the newly added shape.
     */
    fun addMarker(markerDescription: MarkerDescription): Int

    fun addMarkers(markers: List<MarkerDescription>): List<Int>

    /** Sets the icon for a marker.  */
    fun setMarkerIcon(featureId: Int, markerIconDescription: MarkerIconDescription)

    /** Gets the location of an existing marker.  */
    fun getMarkerPoint(featureId: Int): MapPoint?

    /**
     * Adds a polyline to the map with the given sequence of vertices.
     * The vertices will have handles that can be dragged by the user.
     * Returns a positive integer, the featureId for the newly added shape.
     */
    fun addPolyLine(lineDescription: LineDescription): Int

    /**
     * Adds a polygon to the map with given sequence of vertices. * Returns a positive integer,
     * the featureId for the newly added shape.
     */
    fun addPolygon(polygonDescription: PolygonDescription): Int

    /** Appends a vertex to the polyline or polygon specified by featureId.  */
    fun appendPointToPolyLine(featureId: Int, point: MapPoint)

    /**
     * Removes the last vertex of the polyline or polygon specified by featureId.
     * If there are no vertices, does nothing.
     */
    fun removePolyLineLastPoint(featureId: Int)

    /**
     * Returns the vertices of the polyline or polygon specified by featureId, or an
     * empty list if the featureId does not identify an existing polyline or polygon.
     */
    fun getPolyLinePoints(featureId: Int): List<MapPoint>

    /** Removes all map features from the map.  */
    fun clearFeatures()

    /** Sets or clears the callback for a click on the map.  */
    fun setClickListener(listener: PointListener?)

    /** Sets or clears the callback for a long press on the map.  */
    fun setLongPressListener(listener: PointListener?)

    /** Sets or clears the callback for a click on a feature.  */
    fun setFeatureClickListener(listener: FeatureListener?)

    /** Sets or clears the callback for when a drag is completed.  */
    fun setDragEndListener(listener: FeatureListener?)

    /**
     * Enables/disables GPS tracking.  While enabled, the GPS location is shown
     * on the map, the first GPS fix will trigger any pending callbacks set by
     * runOnGpsLocationReady(), and every GPS fix will invoke the callback set
     * by setGpsLocationListener().
     */
    fun setGpsLocationEnabled(enabled: Boolean)

    /** Gets the last GPS location fix, or null if there hasn't been one.  */
    fun getGpsLocation(): MapPoint?

    /** Gets the provider of the last fix, or null if there hasn't been one.  */
    fun getLocationProvider(): String?

    /**
     * Queues a callback to be invoked on the UI thread as soon as a GPS fix is
     * available.  If there already is a location fix, the callback is invoked
     * immediately; otherwise, when a fix is obtained, it will be invoked once.
     * To begin searching for a GPS fix, call setGpsLocationEnabled(true).
     * Activities that set callbacks should call setGpsLocationEnabled(false)
     * in their onStop() or onDestroy() methods, to prevent invalid callbacks.
     */
    fun runOnGpsLocationReady(listener: ReadyListener)

    /**
     * Sets or clears the callback for GPS location updates.  This callback
     * will only be invoked while GPS is enabled with setGpsLocationEnabled().
     */
    fun setGpsLocationListener(listener: PointListener?)

    fun setRetainMockAccuracy(retainMockAccuracy: Boolean)

    /**
     * @return true if the [MapFragment] center has already been set (by [MapFragment.zoomToPoint] for instance).
     */
    fun hasCenter(): Boolean

    fun interface ErrorListener {
        fun onError()
    }

    fun interface ReadyListener {
        fun onReady(mapFragment: MapFragment)
    }

    fun interface PointListener {
        fun onPoint(point: MapPoint)
    }

    fun interface FeatureListener {
        fun onFeature(featureId: Int)
    }

    companion object {
        val INITIAL_CENTER: MapPoint = MapPoint(0.0, -30.0)
        const val INITIAL_ZOOM: Float = 2f
        const val POINT_ZOOM: Float = 16f

        const val KEY_REFERENCE_LAYER: String = "REFERENCE_LAYER"

        @Retention(AnnotationRetention.SOURCE)
        @StringDef(BOTTOM, CENTER)
        annotation class IconAnchor

        const val CENTER: String = "center"
        const val BOTTOM: String = "bottom"
    }
}
