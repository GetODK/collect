package org.odk.collect.maps.markers

import org.odk.collect.maps.MapFragment
import org.odk.collect.maps.MapPoint

data class MarkerDescription(
    val point: MapPoint,
    val isDraggable: Boolean,
    @MapFragment.Companion.IconAnchor val iconAnchor: String,
    val iconDescription: MarkerIconDescription
)
