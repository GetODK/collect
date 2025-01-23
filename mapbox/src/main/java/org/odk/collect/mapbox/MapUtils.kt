package org.odk.collect.mapbox

import android.content.Context
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import org.odk.collect.maps.LineDescription
import org.odk.collect.maps.MapFragment
import org.odk.collect.maps.MapPoint
import org.odk.collect.maps.markers.MarkerDescription
import org.odk.collect.maps.markers.MarkerIconCreator
import org.odk.collect.maps.markers.MarkerIconDescription

object MapUtils {
    fun createPointAnnotation(
        pointAnnotationManager: PointAnnotationManager,
        point: MapPoint,
        draggable: Boolean,
        @MapFragment.Companion.IconAnchor iconAnchor: String,
        iconDrawableId: Int,
        context: Context
    ): PointAnnotation {
        return pointAnnotationManager.create(
            PointAnnotationOptions()
                .withPoint(Point.fromLngLat(point.longitude, point.latitude, point.altitude))
                .withIconImage(MarkerIconCreator.getMarkerIconBitmap(context, MarkerIconDescription(iconDrawableId)))
                .withIconSize(1.0)
                .withSymbolSortKey(10.0)
                .withDraggable(draggable)
                .withTextOpacity(0.0)
                .withIconAnchor(getIconAnchorValue(iconAnchor))
        )
    }

    fun createPointAnnotations(
        context: Context,
        pointAnnotationManager: PointAnnotationManager,
        markerFeatures: List<MarkerDescription>
    ): List<PointAnnotation> {
        val pointAnnotationOptionsList = markerFeatures.map {
            PointAnnotationOptions()
                .withPoint(Point.fromLngLat(it.point.longitude, it.point.latitude, it.point.altitude))
                .withIconImage(MarkerIconCreator.getMarkerIconBitmap(context, it.iconDescription))
                .withIconSize(1.0)
                .withSymbolSortKey(10.0)
                .withDraggable(it.isDraggable)
                .withTextOpacity(0.0)
                .withIconAnchor(getIconAnchorValue(it.iconAnchor))
        }

        return pointAnnotationManager.create(pointAnnotationOptionsList)
    }

    private fun getIconAnchorValue(@MapFragment.Companion.IconAnchor iconAnchor: String): IconAnchor {
        return when (iconAnchor) {
            MapFragment.BOTTOM -> IconAnchor.BOTTOM
            else -> IconAnchor.CENTER
        }
    }

    fun mapPointFromPointAnnotation(pointAnnotation: PointAnnotation): MapPoint {
        // When a symbol is manually dragged, the position is no longer
        // obtained from a GPS reading, so the altitude and standard
        // deviation fields are no longer meaningful; reset them to zero.
        return MapPoint(pointAnnotation.point.latitude(), pointAnnotation.point.longitude(), 0.0, 0.0)
    }

    // To ensure consistent stroke width across map platforms like Mapbox, Google, and OSM,
    // the value for Mapbox needs to be divided by 3.
    fun convertStrokeWidth(lineDescription: LineDescription): Double {
        return (lineDescription.getStrokeWidth() / 3).toDouble()
    }
}
